package dev.wasdevv.minierp.controller;

import dev.wasdevv.minierp.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final DashboardService dashboard;

    public HomeController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("dashboard", dashboard.build());
        return "home";
    }
}
