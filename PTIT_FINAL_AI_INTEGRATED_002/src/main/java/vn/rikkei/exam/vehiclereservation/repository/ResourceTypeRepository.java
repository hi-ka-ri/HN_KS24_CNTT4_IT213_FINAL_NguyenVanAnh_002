package vn.rikkei.exam.vehiclereservation.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.rikkei.exam.vehiclereservation.model.ResourceType;
public interface ResourceTypeRepository extends JpaRepository<ResourceType, String> { }
