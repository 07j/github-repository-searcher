package com.janhavi.githubsearcher.repository;

import com.janhavi.githubsearcher.model.GitHubRepo;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepoRepository extends JpaRepository<GitHubRepo, Long> {

    List<GitHubRepo> findByStarsGreaterThanEqual(int minStars, Sort sort);

    List<GitHubRepo> findByLanguageIgnoreCaseAndStarsGreaterThanEqual(String language, int minStars, Sort sort);
}
