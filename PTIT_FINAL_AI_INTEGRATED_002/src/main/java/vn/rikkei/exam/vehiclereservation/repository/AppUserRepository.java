package vn.rikkei.exam.vehiclereservation.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.rikkei.exam.vehiclereservation.model.AppUser;
public interface AppUserRepository extends JpaRepository<AppUser, String> { }
