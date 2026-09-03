package vn.rikkei.exam.vehiclereservation.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.rikkei.exam.vehiclereservation.model.*;
import vn.rikkei.exam.vehiclereservation.repository.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final AppUserRepository appUserRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final ResourceInventoryRepository resourceInventoryRepository;
    private final ReservationRequestRepository reservationRequestRepository;

    public Map<String, Object> getVehicleAvailability(String resourceType, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        ResourceType rt = findResource(resourceType);
        if (!Boolean.TRUE.equals(rt.getActive())) {
            throw new IllegalArgumentException("Loại phương tiện đang không hoạt động: " + resourceType);
        }

        long days = ChronoUnit.DAYS.between(startDate, endDate);
        List<ResourceInventory> rows = resourceInventoryRepository
                .findByResourceTypeAndAvailableDateBetween(rt, startDate, endDate.minusDays(1));

        Map<LocalDate, Integer> byDate = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = startDate.plusDays(i);
            byDate.put(d, rt.getMaxParticipants() == null ? 0 : rt.getMaxParticipants());
        }
        for (ResourceInventory row : rows) {
            byDate.put(row.getAvailableDate(), Optional.ofNullable(row.getAvailableSlots()).orElse(0));
        }

        List<Map<String, Object>> availability = new ArrayList<>();
        byDate.forEach((date, slots) -> availability.add(Map.of("date", date, "availableSlots", slots)));

        return Map.of(
                "resourceType", rt.getResourceCode(),
                "resourceName", rt.getDisplayName(),
                "startDate", startDate,
                "endDate", endDate,
                "availability", availability
        );
    }

    @Transactional
    public Map<String, Object> createVehicleReservationRequest(
            String userId,
            String resourceType,
            LocalDate startDate,
            LocalDate endDate,
            Integer participantCount,
            String purpose) {

        validateDateRange(startDate, endDate);
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        if (days > 14) throw new IllegalArgumentException("Thời gian đặt xe không được vượt quá 14 ngày");
        if (participantCount == null || participantCount <= 0) throw new IllegalArgumentException("Số người tham gia phải lớn hơn 0");
        if (purpose == null || purpose.trim().length() < 10 || purpose.trim().length() > 200) {
            throw new IllegalArgumentException("Mục đích sử dụng phải có từ 10 đến 200 ký tự");
        }

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng: " + userId));
        ResourceType rt = findResource(resourceType);
        if (!Boolean.TRUE.equals(rt.getActive())) {
            throw new IllegalArgumentException("Loại phương tiện đang không hoạt động: " + resourceType);
        }

        Integer capacity = rt.getMaxParticipants();
        if (capacity != null && participantCount > capacity) {
            throw new IllegalArgumentException("Số người tham gia vượt quá sức chứa của phương tiện là " + capacity + " người");
        }
        if (isPremium(rt) && participantCount < 2) {
            throw new IllegalArgumentException("Phương tiện PREMIUM yêu cầu tối thiểu 2 người tham gia");
        }

        Map<String, Object> availability = getVehicleAvailability(resourceType, startDate, endDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dates = (List<Map<String, Object>>) availability.get("availability");
        for (Map<String, Object> date : dates) {
            int slots = ((Number) date.get("availableSlots")).intValue();
            if (participantCount > slots) {
                throw new IllegalArgumentException(
                        "Không đủ chỗ vào ngày " + date.get("date") + ": số chỗ còn lại=" + slots
                );
            }
        }

        Instant now = Instant.now();
        ReservationRequest request = ReservationRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .requester(user)
                .resourceType(rt)
                .startDate(startDate)
                .endDate(endDate)
                .participantCount(participantCount)
                .purpose(purpose.trim())
                .status(ReservationStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        reservationRequestRepository.save(request);

        return Map.of(
                "status", "PENDING",
                "requestId", request.getRequestId(),
                "summary", Map.of(
                        "userId", userId,
                        "resourceType", rt.getResourceCode(),
                        "resourceName", rt.getDisplayName(),
                        "startDate", startDate,
                        "endDate", endDate,
                        "participantCount", participantCount,
                        "purpose", purpose.trim()
                )
        );
    }

    @Transactional
    public Map<String, Object> approveOrReject(String requestId, String decision, String note) {
        ReservationRequest request = reservationRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Không tìm thấy yêu cầu đặt xe: " + requestId
                ));

        if (request.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("Chỉ có yêu cầu đang ở trạng thái CHỜ XỬ LÝ mới được xử lý");
        }

        if ("APPROVE".equals(decision)) {
            // Kiểm tra lại toàn bộ quy tắc nghiệp vụ ngay trước khi phê duyệt.
            createValidationOnly(request);
            request.setStatus(ReservationStatus.APPROVED);
        } else if ("REJECT".equals(decision)) {
            request.setStatus(ReservationStatus.REJECTED);
        } else {
            throw new IllegalArgumentException("Quyết định phải là APPROVE hoặc REJECT");
        }

        request.setDecisionNote(note == null ? "" : note.trim());
        request.setUpdatedAt(Instant.now());
        reservationRequestRepository.save(request);

        return Map.of(
                "status", request.getStatus().name(),
                "requestId", requestId
        );
    }

    private void createValidationOnly(ReservationRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());

        long days = ChronoUnit.DAYS.between(
                request.getStartDate(),
                request.getEndDate()
        );

        if (days > 14) {
            throw new IllegalStateException("Thời gian đặt xe không được vượt quá 14 ngày");
        }

        ResourceType rt = findResource(
                request.getResourceType().getResourceCode()
        );

        if (!Boolean.TRUE.equals(rt.getActive())) {
            throw new IllegalStateException("Loại phương tiện đang không hoạt động");
        }

        if (rt.getMaxParticipants() != null
                && request.getParticipantCount() > rt.getMaxParticipants()) {
            throw new IllegalStateException("Số người tham gia vượt quá sức chứa của phương tiện");
        }

        if (isPremium(rt) && request.getParticipantCount() < 2) {
            throw new IllegalStateException("Phương tiện PREMIUM yêu cầu tối thiểu 2 người tham gia");
        }

        Map<String, Object> availability = getVehicleAvailability(
                rt.getResourceCode(),
                request.getStartDate(),
                request.getEndDate()
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> dates =
                (List<Map<String, Object>>) availability.get("availability");

        for (Map<String, Object> date : dates) {
            if (request.getParticipantCount()
                    > ((Number) date.get("availableSlots")).intValue()) {

                throw new IllegalStateException(
                        "Không đủ chỗ vào ngày " + date.get("date")
                );
            }
        }
    }

    private ResourceType findResource(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("Loại phương tiện là bắt buộc");
        }

        return resourceTypeRepository
                .findById(resourceType.trim().toUpperCase())
                .orElseThrow(() -> new NoSuchElementException(
                        "Không tìm thấy loại phương tiện: " + resourceType
                ));
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null
                || endDate == null
                || !startDate.isBefore(endDate)) {

            throw new IllegalArgumentException(
                    "Ngày bắt đầu phải trước ngày kết thúc"
            );
        }
    }

    private boolean isPremium(ResourceType rt) {
        return "PREMIUM".equalsIgnoreCase(rt.getResourceCode())
                || "PRM".equalsIgnoreCase(rt.getResourceCode())
                || (rt.getDisplayName() != null
                && rt.getDisplayName().toUpperCase().contains("PREMIUM"));
    }
}
