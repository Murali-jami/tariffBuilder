package com.xius.TariffBuilder.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.xius.TariffBuilder.Dto.LoginRequestDto;
import com.xius.TariffBuilder.Dto.UsrPrivilegeDTO;

@Service
public class UserLoginService {

	private static final Logger logger = LoggerFactory.getLogger(UserLoginService.class);

	@Autowired
	private UserAuthRepository repository;

	public Map<String, Object> authenticate(LoginRequestDto request) {

		logger.info("Authentication started for loginId={} networkName={}", request.getLoginId(),
				request.getNetworkLoginName());

		List<UsrPrivilegeDTO> privileges = repository.getUserPrivileges(request.getNetworkLoginName(),
				request.getLoginId(), request.getPassword());

		logger.debug("Privileges fetched count={}", privileges.size());

		if (privileges.isEmpty()) {

			logger.error("Authentication failed for loginId={} - no privileges found", request.getLoginId());

			throw new RuntimeException("Invalid User Credentials");
		}

		Long networkId = repository.getNetworkId(request.getNetworkLoginName());

		logger.info("Authentication successful loginId={} networkId={}", request.getLoginId(), networkId);

		Map<String, Object> result = new HashMap<>();

		result.put("networkId", networkId);

		result.put("privileges", privileges);

		logger.debug("Authentication result prepared for loginId={}", request.getLoginId());

		return result;
	}
}