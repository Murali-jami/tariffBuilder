package com.xius.TariffBuilder.UserService;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ServicePackageService {

    @Autowired
    @Qualifier("oracleJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    // WORKING

    // public String getDescription(Long servicePackageId, Long networkId) {
    //
    // String sql = """
    // SELECT DESCRIPTION
    // FROM CS_RAT_SERVICE_PACKAGE
    // WHERE SERVICE_PACKAGE_ID = ?
    // AND NETWORK_ID = ?
    // """;
    //
    // List<String> list = jdbcTemplate.query(sql, new Object[] { servicePackageId,
    // networkId },
    // (rs, i) -> rs.getString("DESCRIPTION"));
    //
    // if (list.isEmpty()) {
    // return "Description not found";
    // }
    //
    // return list.get(0);
    // }
    // }

    public String getDescription(Long servicePackageId, Long networkId) {

        String sql = """
                SELECT DESCRIPTION
                FROM CS_RAT_SERVICE_PACKAGE
                WHERE SERVICE_PACKAGE_ID = ?
                AND NETWORK_ID = ?
                """;

        List<String> list = jdbcTemplate.query(sql, new Object[] { servicePackageId, networkId },
                (rs, i) -> rs.getString("DESCRIPTION"));

        if (list.isEmpty()) {

            return "Description not found";
        }

        return list.get(0);
    }
}