package com.autoparts_maven.autoparts_maven.vehicle;

import com.autoparts_maven.autoparts_maven.vehicle.dto.VehicleResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
public class VehicleController {

    private final VehicleService vehicleService;

    @PostMapping("/lookup")
    public VehicleResponse lookup(@Valid @RequestBody PlateLookupRequest request, HttpServletRequest httpRequest) {
        return vehicleService.lookupByPlate(request.plate(), httpRequest.getRemoteAddr());
    }

    @GetMapping("/{id}")
    public VehicleResponse findById(@PathVariable Long id) {
        return vehicleService.findById(id);
    }

    public record PlateLookupRequest(@NotBlank @Size(max = 20) String plate) {
    }
}
