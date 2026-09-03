package vn.rikkei.exam.vehiclereservation.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.rikkei.exam.vehiclereservation.model.ResourceInventory;
import vn.rikkei.exam.vehiclereservation.model.ResourceType;
import java.time.LocalDate;
import java.util.List;

public interface ResourceInventoryRepository extends JpaRepository<ResourceInventory, Long> {
    List<ResourceInventory> findByResourceTypeAndAvailableDateBetween(ResourceType resourceType, LocalDate startDate, LocalDate endDate);
}
