package com.valorank.stats;

import com.valorank.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PremierStatsRepository extends JpaRepository<PremierStats, Long> {
    Optional<PremierStats> findByUser(User user);
}
