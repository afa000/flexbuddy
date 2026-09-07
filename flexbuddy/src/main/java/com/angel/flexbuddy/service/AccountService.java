package com.angel.flexbuddy.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;

@Service
public class AccountService {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public boolean emailIsRegistered(String email) {
        return email != null && userRepository.existsByEmailIgnoreCase(email.trim());
    }

    @Transactional
    public AppUser register(RegistrationRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        AppUser user = new AppUser(
                request.getDisplayName().trim(),
                email,
                passwordEncoder.encode(request.getPassword())
        );
        return userRepository.save(user);
    }
}
