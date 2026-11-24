package com.autoparts_maven.autoparts_maven.returns;

import com.autoparts_maven.autoparts_maven.common.PageResponse;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.CreateReturnRequest;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.NoteRequest;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.ReasonRequest;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.ReceiveRequest;
import com.autoparts_maven.autoparts_maven.returns.dto.ReturnDtos.ReturnResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer returns of delivered orders and the admins' handling of them (/api/admin/** is ADMIN only). */
@RestController
class ReturnController {

    private final ReturnService returnService;

    ReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @PostMapping("/api/orders/{orderId}/returns")
    ResponseEntity<ReturnResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId,
            @Valid @RequestBody CreateReturnRequest request) {
        ReturnResponse created = returnService.create(userId(jwt), orderId, request);
        return ResponseEntity.created(URI.create("/api/orders/" + orderId + "/returns")).body(created);
    }

    @GetMapping("/api/orders/{orderId}/returns")
    List<ReturnResponse> listMine(@AuthenticationPrincipal Jwt jwt, @PathVariable Long orderId) {
        return returnService.listMine(userId(jwt), orderId);
    }

    /** Oldest first, so requests closest to their deadlines come on top. */
    @GetMapping("/api/admin/returns")
    PageResponse<ReturnResponse> search(@RequestParam(required = false) ReturnStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return returnService.search(status, pageable);
    }

    @GetMapping("/api/admin/returns/{id}")
    ReturnResponse get(@PathVariable Long id) {
        return returnService.get(id);
    }

    @PostMapping("/api/admin/returns/{id}/approve")
    ReturnResponse approve(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody(required = false) NoteRequest request) {
        return returnService.approve(id, userId(jwt), request == null ? null : request.note());
    }

    @PostMapping("/api/admin/returns/{id}/receive")
    ReturnResponse receive(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ReceiveRequest request) {
        return returnService.receive(id, userId(jwt), request.restock(), request.note());
    }

    @PostMapping("/api/admin/returns/{id}/refund")
    ReturnResponse refund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return returnService.refund(id, userId(jwt));
    }

    @PostMapping("/api/admin/returns/{id}/replace")
    ReturnResponse replace(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody(required = false) NoteRequest request) {
        return returnService.replace(id, userId(jwt), request == null ? null : request.note());
    }

    @PostMapping("/api/admin/returns/{id}/reject")
    ReturnResponse reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody ReasonRequest request) {
        return returnService.reject(id, userId(jwt), request.reason());
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
