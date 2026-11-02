package org.birdbro.lumen.auth.service.impl;

import org.birdbro.lumen.auth.dto.CreateUserRequest;
import org.birdbro.lumen.auth.entity.UserInfo;
import org.birdbro.lumen.auth.mapper.UserInfoMapper;
import org.birdbro.lumen.auth.service.UserInfoService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * User management implementation (auth-rbac-foundation task 3.3). The audit identity
 * ({@code create_by}) is NOT set here on purpose: the copied {@code MetaObjectConfig}
 * insert fill owns it, sourced from the USER-HEADER request header that the lumen
 * {@code UserHeaderBridgeFilter} derives from the JWT principal (gateway-injected in
 * OPS; bridge-filtered in lumen - see that filter's javadoc for the rationale).
 *
 * @author birdbro
 */
@Service
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoMapper userInfoMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserInfoServiceImpl(UserInfoMapper userInfoMapper) {
        this.userInfoMapper = userInfoMapper;
    }

    @Override
    public Integer createUser(CreateUserRequest request) {
        UserInfo user = new UserInfo();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        // must_change_password defaults 0 and enable defaults 1 (V1 column defaults).
        userInfoMapper.insert(user);
        return user.getId();
    }
}
