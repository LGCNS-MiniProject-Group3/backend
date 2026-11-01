package com.recu.content.database.repository;

import com.recu.content.database.entity.CompanyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {
  Optional<CompanyEntity> findByName(String name);
}
