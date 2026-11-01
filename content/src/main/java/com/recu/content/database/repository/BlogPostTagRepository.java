package com.recu.content.database.repository;

import com.recu.content.database.entity.BlogPostTagEntity;
import com.recu.content.database.entity.BlogPostTagPk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlogPostTagRepository extends JpaRepository<BlogPostTagEntity, BlogPostTagPk> {}
