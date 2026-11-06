package com.crmgenerator.web;

import com.crmgenerator.service.RegistrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthPageController {

    private final RegistrationService registration;

    public AuthPageController(RegistrationService registration) {
        this.registration = registration;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String business_name,
                           @RequestParam String owner_name,
                           @RequestParam String email,
                           @RequestParam String password,
                           @RequestParam(required = false) String password_confirm,
                           @RequestParam(required = false) String phone,
                           RedirectAttributes ra,
                           Model model) {
        try {
            if (password_confirm != null && !password.equals(password_confirm)) {
                throw new IllegalArgumentException("Las contraseñas no coinciden");
            }
            registration.registerBusiness(business_name, owner_name, email, password, phone);
            ra.addFlashAttribute("success", "Negocio registrado. Ya puedes ingresar.");
            return "redirect:/login?registered";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("business_name", business_name);
            model.addAttribute("owner_name", owner_name);
            model.addAttribute("email", email);
            model.addAttribute("phone", phone);
            return "auth/register";
        }
    }
}
