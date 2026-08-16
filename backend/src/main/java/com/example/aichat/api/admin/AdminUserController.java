package com.example.aichat.api.admin;

import com.example.aichat.api.admin.AdminDtos.AdminUserResponse;
import com.example.aichat.api.admin.AdminDtos.ChangeRoleRequest;
import com.example.aichat.api.admin.AdminDtos.CreateUserRequest;
import com.example.aichat.application.auth.CurrentUser;
import com.example.aichat.application.user.AdminUserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService users;

    public AdminUserController(AdminUserService users) {
        this.users = users;
    }

    @GetMapping
    public List<AdminUserResponse> list() {
        return users.list().stream().map(AdminUserResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return AdminUserResponse.of(users.create(new AdminUserService.CreateUserCommand(request.username(),
                request.email(), request.password(), request.displayName(), request.role())));
    }

    @PostMapping("/{id}/disable")
    public AdminUserResponse disable(@AuthenticationPrincipal CurrentUser actor, @PathVariable("id") UUID id) {
        return AdminUserResponse.of(users.disable(actor.id(), id));
    }

    /** Re-activates a disabled or locked account and clears failed login attempts. */
    @PostMapping("/{id}/reset-status")
    public AdminUserResponse resetStatus(@PathVariable("id") UUID id) {
        return AdminUserResponse.of(users.resetStatus(id));
    }

    @PutMapping("/{id}/role")
    public AdminUserResponse changeRole(@AuthenticationPrincipal CurrentUser actor, @PathVariable("id") UUID id,
                                        @Valid @RequestBody ChangeRoleRequest request) {
        return AdminUserResponse.of(users.changeRole(actor.id(), id, request.role()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser actor, @PathVariable("id") UUID id) {
        users.delete(actor.id(), id);
    }
}
