package co.postscriptum.controller;

import co.postscriptum.db.DB;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/test")
@AllArgsConstructor
public class IntegrationTestController {

    private final DB db;

    @GetMapping("/expireUser")
    public void expireUser(@RequestParam("userUuid") String userUuid) {

        log.info("Expire user.uuid: {}", userUuid);

        db.withLoadedAccountByUuid(userUuid, account -> {
            account.getUserData().getInternal().getUserPlan().setPaidUntil(0);
        });

    }

}
