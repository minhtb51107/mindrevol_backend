package com.example.demo.repository.chat.UserPreferenceRepository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.chat.UserPreference;

//UserPreferenceRepository  
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
 Optional<UserPreference> findByUserId(Long userId);
}