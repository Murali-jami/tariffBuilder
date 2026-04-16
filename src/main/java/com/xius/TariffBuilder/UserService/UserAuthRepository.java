//package com.xius.TariffBuilder.UserService;
//
//import java.nio.charset.StandardCharsets;
//import java.security.MessageDigest;
//import java.util.List;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.stereotype.Repository;
//
//import com.xius.TariffBuilder.Dto.UsrPrivilegeDTO;
//
//@Repository
//public class UserAuthRepository {
//
//	private static final Logger logger = LoggerFactory.getLogger(UserAuthRepository.class);
//
//	@Autowired
//	private JdbcTemplate jdbcTemplate;
//
//	public List<UsrPrivilegeDTO> getUserPrivileges(String networkName, String loginId, String password) {
//
//		logger.info("Fetching user privileges for loginId={} networkName={}", loginId, networkName);
//
//		String sql = """
//
//				SELECT DISTINCT
//				       u.network_id,
//				       n.status_code network_status,
//				       u.status_code user_status,
//				       p.privilege_id,
//				       p.privilege_name,
//				       p.privilege_desc,
//				       p.module_id
//
//				FROM ums_mt_user u
//
//				JOIN glb_mt_network n
//				ON n.network_id = u.network_id
//
//
//				LEFT JOIN ums_tt_user_roles r
//				ON r.login_id = u.login_id
//				AND r.network_id = u.network_id
//
//
//				LEFT JOIN ums_tt_roleprofile rp
//				ON rp.role_id = r.role_id
//
//
//				LEFT JOIN ums_mt_privilege p
//				ON p.privilege_id = rp.privilege_id
//
//
//				WHERE upper(n.network_display)=upper(?)
//
//				AND upper(u.login_id)=upper(?)
//
//				AND u.password_name = ?
//
//				""";
//
//		List<UsrPrivilegeDTO> result = jdbcTemplate.query(
//
//				sql,
//
//				(rs, rowNum) -> {
//
//					UsrPrivilegeDTO dto = new UsrPrivilegeDTO();
//
//					dto.setPrivilegeId(rs.getString("privilege_id"));
//
//					dto.setPrivilegeName(rs.getString("privilege_name"));
//
//					dto.setPrivilegeDesc(rs.getString("privilege_desc"));
//
//					dto.setModuleId(rs.getString("module_id"));
//
//					return dto;
//
//				},
//
//				networkName, loginId, sha1(password));
//
//		logger.info("Privileges fetched count={} for loginId={}", result.size(), loginId);
//
//		return result;
//	}
//
//	public Long getNetworkId(String networkName) {
//
//		logger.info("Fetching networkId for networkName={}", networkName);
//
//		String sql = """
//
//				SELECT network_id
//
//				FROM glb_mt_network
//
//				WHERE upper(network_display)=upper(?)
//
//				""";
//
//		Long networkId = jdbcTemplate.queryForObject(sql, Long.class, networkName);
//
//		logger.info("Fetched networkId={} for networkName={}", networkId, networkName);
//
//		return networkId;
//	}
//
//	private String sha1(String input) {
//
//		logger.debug("Generating SHA1 hash");
//
//		try {
//
//			MessageDigest md = MessageDigest.getInstance("SHA-1");
//
//			byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
//
//			StringBuilder hex = new StringBuilder();
//
//			for (byte b : bytes) {
//
//				hex.append(String.format("%02X", b));
//			}
//
//			String hashedValue = hex.toString();
//
//			logger.debug("SHA1 hash generated successfully");
//
//			return hashedValue;
//
//		} catch (Exception e) {
//
//			logger.error("Error while generating SHA1 hash", e);
//
//			throw new RuntimeException(e);
//		}
//	}
//}

















package com.xius.TariffBuilder.UserService;
 
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
 
import com.xius.TariffBuilder.Dto.UsrPrivilegeDTO;
 
@Repository
public class UserAuthRepository {
 
    private static final Logger logger = LoggerFactory.getLogger(UserAuthRepository.class);
 
    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate jdbcTemplate;
 
 
    // LOGIN PRIVILEGES
    public List<UsrPrivilegeDTO> getUserPrivileges(String networkName, String loginId, String password) {
 
        logger.info("Fetching user privileges for loginId={} networkName={}", loginId, networkName);
 
        String sql = """
 
        SELECT DISTINCT
               u.network_id,
               n.status_code network_status,
               u.status_code user_status,
               p.privilege_id,
               p.privilege_name,
               p.privilege_desc,
               p.module_id
 
        FROM sim_upload_automation.ums_mt_user u
 
        JOIN sim_upload_automation.glb_mt_network n
        ON n.network_id = u.network_id
 
        LEFT JOIN sim_upload_automation.ums_tt_user_roles r
        ON r.login_id = u.login_id
        AND r.network_id = u.network_id
 
        LEFT JOIN sim_upload_automation.ums_tt_roleprofile rp
        ON rp.role_id = r.role_id
 
        LEFT JOIN sim_upload_automation.ums_mt_privilege p
        ON p.privilege_id = rp.privilege_id
 
        WHERE upper(n.network_display)=upper(?)
        AND upper(u.login_id)=upper(?)
        AND u.password_name = ?
 
        """;
 
 
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
 
                    UsrPrivilegeDTO dto = new UsrPrivilegeDTO();
 
                    dto.setPrivilegeId(rs.getString("privilege_id"));
                    dto.setPrivilegeName(rs.getString("privilege_name"));
                    dto.setPrivilegeDesc(rs.getString("privilege_desc"));
                    dto.setModuleId(rs.getString("module_id"));
 
                    return dto;
                },
                networkName,
                loginId,
                sha1(password)
        );
    }
 
 
    // NETWORK ID
    public Long getNetworkId(String networkName) {
 
        logger.info("Fetching networkId for networkName={}", networkName);
 
        String sql = """
 
        SELECT network_id
        FROM sim_upload_automation.glb_mt_network
        WHERE upper(network_display)=upper(?)
 
        """;
 
        return jdbcTemplate.queryForObject(sql, Long.class, networkName);
    }
 
 
 
    // PASSWORD HASH
    private String sha1(String input) {
 
        try {
 
            MessageDigest md = MessageDigest.getInstance("SHA-1");
 
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
 
            StringBuilder hex = new StringBuilder();
 
            for (byte b : bytes) {
 
                hex.append(String.format("%02X", b));
            }
 
            return hex.toString();
 
        } catch (Exception e) {
 
            throw new RuntimeException(e);
        }
    }
 
}