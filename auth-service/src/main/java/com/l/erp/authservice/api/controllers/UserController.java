package com.l.erp.authservice.api.controllers;

import com.l.erp.authservice.api.dto.UserAccountPageDTO;
import com.l.erp.authservice.infra.config.Roles;
import com.l.erp.authservice.services.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/users")
public class UserController {

    private final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> createUser(){
        log.debug("REST request to create a user");
        return ResponseEntity.ok().build();
    }

    @GetMapping("")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<Page<UserAccountPageDTO>> getAllUsers(@PageableDefault(size = 10, sort = "displayName") Pageable pageable){
        log.debug("REST request to get all users");
        return ResponseEntity.ok(userService.getAllAccounts(pageable));
    }

    @GetMapping("/{userId}")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> getUserById(){
        log.debug("REST request to get a user by id");
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{userId}")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> updateUserById(){
        log.debug("REST request to update a user by id");
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{userId}/status")
    @Secured({Roles.APP_OWNER,Roles.TENANT_OWNER})
    public ResponseEntity<String> updateUserStatusById(){
        log.debug("REST request to update the status of the given user");
        return ResponseEntity.ok().build();
    }
}
