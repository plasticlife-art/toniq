package app.rubeton.toniq.user;

import app.rubeton.toniq.security.ManagerRole;
import io.jmix.core.security.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ManagerSeedMigrationTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    DataSource dataSource;

    @Test
    void managerUserAndRoleAssignmentAreSeeded() throws Exception {
        UserDetails userDetails = userRepository.loadUserByUsername("manager");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("manager");
        assertThat(userDetails.getPassword()).isEqualTo("{noop}TqM9!vR2#sL8@kN4");
        assertThat(countManagerRoleAssignments()).isEqualTo(1);
    }

    private int countManagerRoleAssignments() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select count(*) from SEC_ROLE_ASSIGNMENT where USERNAME = ? and ROLE_CODE = ? and ROLE_TYPE = ?")) {
            statement.setString(1, "manager");
            statement.setString(2, ManagerRole.CODE);
            statement.setString(3, "resource");

            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }
}
