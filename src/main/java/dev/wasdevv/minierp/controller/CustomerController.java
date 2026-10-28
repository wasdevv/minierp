package dev.wasdevv.minierp.controller;

import dev.wasdevv.minierp.dto.CustomerForm;
import dev.wasdevv.minierp.exception.DuplicateDocumentException;
import dev.wasdevv.minierp.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private static final String FORM = "customers/form";
    private static final Sort LIST_ORDER = Sort.by("name", "id");

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public String list(@RequestParam(name = "q", required = false) String query,
                       @PageableDefault(size = 10) Pageable pageable,
                       Model model) {
        // Sorting is fixed: a client-supplied ?sort= on an unknown property would otherwise be a 500.
        Pageable page = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), LIST_ORDER);
        model.addAttribute("page", service.search(query, page));
        model.addAttribute("q", query == null ? "" : query.strip());
        return "customers/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("customer", new CustomerForm());
        return FORM;
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("customer") CustomerForm form, BindingResult result,
                         RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return FORM;
        }
        try {
            service.create(form);
        } catch (DuplicateDocumentException e) {
            rejectDuplicateDocument(result);
            return FORM;
        }
        redirect.addFlashAttribute("notice", "Cliente cadastrado.");
        return "redirect:/customers";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("customer", CustomerForm.from(service.get(id)));
        model.addAttribute("customerId", id);
        return FORM;
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("customer") CustomerForm form,
                         BindingResult result, Model model, RedirectAttributes redirect) {
        model.addAttribute("customerId", id);
        if (result.hasErrors()) {
            service.get(id);
            return FORM;
        }
        try {
            service.update(id, form);
        } catch (DuplicateDocumentException e) {
            rejectDuplicateDocument(result);
            return FORM;
        }
        redirect.addFlashAttribute("notice", "Cliente atualizado.");
        return "redirect:/customers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes redirect) {
        service.delete(id);
        redirect.addFlashAttribute("notice", "Cliente excluído.");
        return "redirect:/customers";
    }

    private static void rejectDuplicateDocument(BindingResult result) {
        result.rejectValue("document", "duplicate", "Já existe um cliente com este CPF/CNPJ.");
    }
}
