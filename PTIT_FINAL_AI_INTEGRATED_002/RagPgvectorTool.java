import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Minimal Java tool for:
 * - Chunking text by headings
 * - Getting embeddings (OpenAI) - placeholder (set OPENAI_API_KEY env)
 * - Ingesting into Postgres+pgvector with dedup via SHA-256 content hash
 * - Retrieving nearest chunks and returning sources
 *
 * Notes:
 * - Requires dependencies: jackson-databind and PostgreSQL JDBC driver
 * - Set env vars: OPENAI_API_KEY, PG_JDBC_URL (e.g., jdbc:postgresql://host:5432/db), PG_USER, PG_PASSWORD
 */
public class RagPgvectorTool {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java RagPgvectorTool ingest <docId> <filePath> | retrieve <query>");
            return;
        }
        String cmd = args[0];
        if ("ingest".equalsIgnoreCase(cmd)) {
            if (args.length < 3) { System.err.println("ingest <docId> <filePath>"); return; }
            String docId = args[1];
            String filePath = args[2];
            String content = java.nio.file.Files.readString(java.nio.file.Path.of(filePath), StandardCharsets.UTF_8);
            List<Chunk> chunks = chunkByHeadings(content);
            try (Connection conn = getConn()) {
                createTableIfNotExists(conn);
                int idx = 0;
                for (Chunk c : chunks) {
                    float[] emb = getEmbedding(c.text);
                    ingestChunk(conn, docId, idx++, c, emb);
                }
            }
            System.out.println("Ingest complete. Chunks: " + chunks.size());
        } else if ("retrieve".equalsIgnoreCase(cmd)) {
            if (args.length < 2) { System.err.println("retrieve <query>"); return; }
            String query = args[1];
            float[] qemb = getEmbedding(query);
            try (Connection conn = getConn()) {
                List<Map<String,Object>> rows = retrieve(conn, qemb, 5);
                Map<String,Object> response = new LinkedHashMap<>();
                response.put("conversationId", UUID.randomUUID().toString());
                response.put("answer", "See resources for retrieved chunks.");
                response.put("resources", rows);
                response.put("toolsUsed", List.of("RagPgvectorTool","OpenAI-embeddings","Postgres+pgvector"));
                System.out.println(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(response));
            }
        } else {
            System.err.println("Unknown cmd");
        }
    }

    static Connection getConn() throws SQLException {
        String url = System.getenv().getOrDefault("PG_JDBC_URL", System.getenv("JDBC_DATABASE_URL"));
        String user = System.getenv("PG_USER");
        String pass = System.getenv("PG_PASSWORD");
        if (url == null) throw new IllegalStateException("PG_JDBC_URL or JDBC_DATABASE_URL must be set");
        return DriverManager.getConnection(url, user, pass);
    }

    static class Chunk { public String title; public String text; public Map<String,String> metadata = new HashMap<>(); }

    static List<Chunk> chunkByHeadings(String content) {
        List<Chunk> out = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");
        StringBuilder cur = new StringBuilder();
        String curTitle = "root";
        for (String line : lines) {
            if (line.strip().startsWith("#")) {
                if (cur.length() > 0) {
                    Chunk c = new Chunk(); c.title = curTitle; c.text = cur.toString().trim(); c.metadata.put("title", curTitle); out.add(c);
                }
                cur.setLength(0);
                curTitle = line.replaceAll("^#+",""").trim();
            } else {
                cur.append(line).append("\n");
            }
        }
        if (cur.length() > 0) {
            Chunk c = new Chunk(); c.title = curTitle; c.text = cur.toString().trim(); c.metadata.put("title", curTitle); out.add(c);
        }
        // fallback: if no headings, split into 1000-char windows
        if (out.isEmpty()) {
            int window = 1000; int i=0; while (i < content.length()) { int end = Math.min(content.length(), i+window); Chunk c = new Chunk(); c.title = "chunk_" + (i/window); c.text = content.substring(i,end); c.metadata.put("title", c.title); out.add(c); i = end; }
        }
        return out;
    }

    static float[] getEmbedding(String text) throws IOException, InterruptedException {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) throw new IllegalStateException("OPENAI_API_KEY env required for embeddings");
        Map<String,Object> body = new HashMap<>();
        body.put("input", text);
        body.put("model", "text-embedding-3-large");
        String req = MAPPER.writeValueAsString(body);
        HttpRequest r = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/embeddings"))
            .header("Authorization","Bearer "+apiKey)
            .header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofString(req))
            .build();
        HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("Embedding API failed: " + resp.body());
        JsonNode root = MAPPER.readTree(resp.body());
        JsonNode emb = root.at("/data/0/embedding");
        float[] arr = new float[emb.size()];
        for (int i=0;i<emb.size();i++) arr[i] = (float) emb.get(i).asDouble();
        return arr;
    }

    static String sha256Hex(String s) {
        try { MessageDigest md = MessageDigest.getInstance("SHA-256"); byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8)); StringBuilder sb = new StringBuilder(); for (byte x: b) sb.append(String.format("%02x", x)); return sb.toString(); } catch (Exception e) { throw new RuntimeException(e); }
    }

    static void createTableIfNotExists(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            st.execute("CREATE TABLE IF NOT EXISTS documents (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), doc_id TEXT, chunk_index INT, content TEXT, content_hash TEXT UNIQUE, metadata JSONB, embedding VECTOR(1536))");
            st.execute("CREATE INDEX IF NOT EXISTS idx_documents_embedding ON documents USING ivfflat (embedding vector_l2_ops) WITH (lists = 100)");
        }
    }

    static void ingestChunk(Connection conn, String docId, int idx, Chunk c, float[] emb) throws SQLException {
        String hash = sha256Hex(c.text);
        String sql = "INSERT INTO documents (doc_id, chunk_index, content, content_hash, metadata, embedding) VALUES (?,?,?,?,?::jsonb, ?) ON CONFLICT (content_hash) DO NOTHING";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, docId);
            ps.setInt(2, idx);
            ps.setString(3, c.text);
            ps.setString(4, hash);
            ps.setString(5, MAPPER.writeValueAsString(c.metadata));
            // set embedding as PostgreSQL array literal: quote like '[0.1,0.2,...]'
            ps.setString(6, vectorToPgLiteral(emb));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new SQLException("Insert failed", e);
        }
    }

    static String vectorToPgLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(); sb.append('[');
        for (int i=0;i<v.length;i++) { if (i>0) sb.append(','); sb.append(v[i]); }
        sb.append(']');
        return sb.toString();
    }

    static List<Map<String,Object>> retrieve(Connection conn, float[] qemb, int k) throws SQLException {
        String sql = "SELECT doc_id, chunk_index, content, metadata, embedding <-> ? AS distance FROM documents ORDER BY distance ASC LIMIT ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, vectorToPgLiteral(qemb));
            ps.setInt(2, k);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                while (rs.next()) {
                    Map<String,Object> row = new HashMap<>();
                    row.put("doc_id", rs.getString("doc_id"));
                    row.put("chunk_index", rs.getInt("chunk_index"));
                    row.put("content", rs.getString("content"));
                    row.put("metadata", rs.getString("metadata"));
                    row.put("distance", rs.getDouble("distance"));
                    out.add(row);
                }
                return out;
            }
        }
    }
}
