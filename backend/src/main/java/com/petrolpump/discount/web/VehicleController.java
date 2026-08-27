package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.VehicleLink;
import com.petrolpump.discount.repo.VehicleLinkRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.VehicleNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final AuthService auth;
    private final VehicleLinkRepository vehicles;

    public VehicleController(AuthService auth, VehicleLinkRepository vehicles) {
        this.auth = auth;
        this.vehicles = vehicles;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestHeader("X-Session-Token") String token) {
        var user = auth.requireUser(token);
        return vehicles.findByUser(user).stream().map(v -> Map.<String, Object>of(
                "id", v.getId(), "regNo", v.getRegNo(), "fuelType", v.getFuelType() == null ? "" : v.getFuelType()
        )).toList();
    }

    @PostMapping
    public Map<String, Object> add(@RequestHeader("X-Session-Token") String token, @RequestBody Map<String, String> body) {
        var user = auth.requireUser(token);
        String reg = VehicleNormalizer.normalize(body.get("regNo"));
        if (reg == null || reg.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid vehicle number");
        }
        if (vehicles.existsByUserAndRegNo(user, reg)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This vehicle is already linked to your account.");
        }
        VehicleLink v = new VehicleLink();
        v.setUser(user);
        v.setRegNo(reg);
        v.setFuelType(body.get("fuelType"));
        try {
            vehicles.saveAndFlush(v);
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This vehicle is already linked to your account.");
        }
        return Map.of("id", v.getId(), "regNo", v.getRegNo());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@RequestHeader("X-Session-Token") String token, @PathVariable Long id) {
        var user = auth.requireUser(token);
        var v = vehicles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle not found"));
        if (!v.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed");
        }
        vehicles.delete(v);
        return Map.of("status", "ok");
    }
}
