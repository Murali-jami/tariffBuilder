// package com.xius.TariffBuilder.Controller;

// import java.util.List;
// import java.util.Map;

// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.http.HttpStatus;
// import org.springframework.web.bind.annotation.RequestMapping;
// import org.springframework.web.bind.annotation.RequestParam;
// import org.springframework.web.bind.annotation.RestController;

// import com.xius.TariffBuilder.UserService.PeriodicChargeService;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.PathVariable;


// @RestController
// @RequestMapping("/periodic_charges")
// public class PeriodicController {

//     @Autowired
//     private PeriodicChargeService periodicChargeService;

//     @GetMapping("/{networkId}")
//     public List<Map<String, Object>> getMethodName(
//             @PathVariable Long networkId,
//             @RequestParam(required = false) String currentChargeId) {
//         // TODO: handle exception
//         if (currentChargeId != null && !currentChargeId.isBlank()) {
//             return periodicChargeService.getPeriodicCharges(networkId, currentChargeId);
//         }
//         return periodicChargeService.getPeriodicCharges(networkId);
//     }
// }