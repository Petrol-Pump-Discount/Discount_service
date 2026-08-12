package com.petrolpump.discount.web;

import com.petrolpump.discount.domain.VehicleLink;
import com.petrolpump.discount.repo.VehicleLinkRepository;
import com.petrolpump.discount.service.AuthService;
import com.petrolpump.discount.service.VehicleNormalizer;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {
    private final AuthService auth;
    private final VehicleLinkRepository vehicles;
    public VehicleController(AuthService auth, VehicleLinkRepository vehicles) {
        this.auth = auth; this.vehicles = vehicles;
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
        VehicleLink v = new VehicleLink();
        v.setUser(user);
        v.setRegNo(VehicleNormalizer.normalize(body.get("regNo")));
        v.setFuelType(body.get("fuelType"));
        vehicles.save(v);
        return Map.of("id", v.getId(), "regNo", v.getRegNo());
    }

    @DeleteMapping("/{id}")
    public Map<String, String> delete(@RequestHeader("X-Session-Token") String token, @PathVariable Long id) {
        var user = auth.requireUser(token);
        var v = vehicles.findById(id).orElseThrow();
        if (!v.getUser().getId().equals(user.getId())) throw new RuntimeException("Forbidden");
        vehicles.delete(v);
        return Map.of("status", "ok");
    }
}
