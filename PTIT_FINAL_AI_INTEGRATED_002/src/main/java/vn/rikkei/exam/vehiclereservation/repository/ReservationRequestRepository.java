package vn.rikkei.exam.vehiclereservation.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.rikkei.exam.vehiclereservation.model.ReservationRequest;
public interface ReservationRequestRepository extends JpaRepository<ReservationRequest, String> { }
