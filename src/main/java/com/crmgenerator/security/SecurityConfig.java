package com.crmgenerator.security;

import com.crmgenerator.domain.UserAccount;
import com.crmgenerator.repository.UserAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(UserAccountRepository users) {
        return username -> {
            UserAccount u = users.findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
            List<SimpleGrantedAuthority> auths = new ArrayList<>();
            String role = u.getRole() == null ? "user" : u.getRole().toLowerCase();
            if (role.equals("admin")) role = "creator";
            if (role.equals("seller")) role = "user";
            if (role.equals("viewer")) role = "visitor";
            auths.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            if (u.isFullAccess()) {
                auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
            return User.withUsername(u.getEmail())
                    .password(u.getPassword())
                    .authorities(auths)
                    .build();
        };
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/css/**", "/js/**", "/uploads/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "CREATOR", "OWNER")
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout.logoutSuccessUrl("/login?logout").permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
        return http.build();
    }
}
