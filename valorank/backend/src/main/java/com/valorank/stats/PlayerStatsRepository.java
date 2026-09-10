package com.valorank.stats;

import com.valorank.stats.PlayerStats;
import com.valorank.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerStatsRepository extends JpaRepository<PlayerStats, Long> {
    Optional<PlayerStats> findByUser(User user);
    Optional<PlayerStats> findByUserId(Long userId);
}


