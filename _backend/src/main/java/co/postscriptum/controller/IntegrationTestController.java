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
    public void expireUser(@RequestParam("username") String username) {

        log.info("expire user: {}", username);

        db.withLoadedAccountByUsername(username, account -> {
            account.getUserData().getInternal().getUserPlan().setPaidUntil(0);
        });

    }

}
