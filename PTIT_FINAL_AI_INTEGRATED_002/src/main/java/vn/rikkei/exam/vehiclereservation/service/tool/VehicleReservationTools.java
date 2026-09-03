package vn.rikkei.exam.vehiclereservation.service.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import vn.rikkei.exam.vehiclereservation.service.ReservationService;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

@Component
@RequiredArgsConstructor
public class VehicleReservationTools {

    private final ReservationService reservationService;
    private final ThreadLocal<Set<String>> executedTools =
            ThreadLocal.withInitial(LinkedHashSet::new);

    @Tool(description = "Lấy thông tin số chỗ còn trống của một loại phương tiện trong khoảng thời gian. Sử dụng công cụ này cho các câu hỏi về tình trạng chỗ trống thực tế. Service Java sẽ thực hiện kiểm tra dữ liệu và truy vấn cơ sở dữ liệu.")
    public Map<String, Object> getVehicleAvailability(
            @ToolParam(description = "Mã loại phương tiện/tài nguyên, ví dụ STD hoặc PRM")
            String resourceType,

            @ToolParam(description = "Ngày bắt đầu theo định dạng yyyy-MM-dd")
            LocalDate startDate,

            @ToolParam(description = "Ngày kết thúc theo định dạng yyyy-MM-dd; phải sau ngày bắt đầu")
            LocalDate endDate) {

        executedTools.get().add("getVehicleAvailability");

        return reservationService.getVehicleAvailability(
                resourceType,
                startDate,
                endDate
        );
    }

    @Tool(description = "Tạo yêu cầu đặt phương tiện. Chỉ tạo yêu cầu ở trạng thái PENDING sau khi đã được Java kiểm tra người dùng, ngày đặt, thời gian tối đa 14 ngày, sức chứa, số người tối thiểu đối với phương tiện PREMIUM, độ dài mục đích sử dụng và tình trạng chỗ trống.")
    public Map<String, Object> createVehicleReservationRequest(
            @ToolParam(description = "ID người dùng đã tồn tại trong hệ thống")
            String userId,

            @ToolParam(description = "Mã loại phương tiện/tài nguyên, ví dụ STD hoặc PRM")
            String resourceType,

            @ToolParam(description = "Ngày bắt đầu theo định dạng yyyy-MM-dd")
            LocalDate startDate,

            @ToolParam(description = "Ngày kết thúc theo định dạng yyyy-MM-dd; phải sau ngày bắt đầu")
            LocalDate endDate,

            @ToolParam(description = "Số lượng người tham gia")
            Integer participantCount,

            @ToolParam(description = "Mục đích sử dụng, từ 10 đến 200 ký tự")
            String purpose) {

        executedTools.get().add("createVehicleReservationRequest");

        return reservationService.createVehicleReservationRequest(
                userId,
                resourceType,
                startDate,
                endDate,
                participantCount,
                purpose
        );
    }

    public Set<String> consumeExecutedTools() {
        Set<String> result = Set.copyOf(executedTools.get());
        executedTools.remove();
        return result;
    }
}