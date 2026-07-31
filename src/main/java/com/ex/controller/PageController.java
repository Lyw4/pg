package com.ex.controller;

import com.ex.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class PageController {

    private final ProductCatalogService productCatalogService;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("siteName", "FEED FLOW");
        model.addAttribute("products", productCatalogService.findProducts(null, null));
        return "index";
    }

    @GetMapping("/admin")
    public String admin(Model model) {
        model.addAttribute("siteName", "FEED FLOW 판매자센터");
        return "admin";
    }
}
