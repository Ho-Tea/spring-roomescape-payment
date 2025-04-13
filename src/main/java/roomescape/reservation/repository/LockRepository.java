package roomescape.reservation.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import lombok.extern.slf4j.Slf4j;
import roomescape.exception.PaymentException;
import roomescape.exception.RoomescapeException;

@Slf4j
@Repository
public class LockRepository {

    private static final String GET_LOCK = "SELECT GET_LOCK(?, ?)";
    private static final String RELEASE_LOCK = "SELECT RELEASE_LOCK(?)";
    private static final String EXCEPTION_MESSAGE = "NAMED LOCK 을 수행하는 중에 오류가 발생하였습니다.";

    private final DataSource dataSource;

    public LockRepository(@Qualifier("distributedDataSource") DataSource distributedDataSource) {
        this.dataSource = distributedDataSource;
    }

    public <T> T executeWithLock(String key,
                                 int timeoutSeconds,
                                 Supplier<T> supplier) {

        try (Connection connection = dataSource.getConnection()) {
            try {
                log.info("Start getLock={}, timeoutSeconds={}, connection={}", key, timeoutSeconds, connection);
                getLock(connection, key, timeoutSeconds);
                log.info("Success getLock={}, timeoutSeconds={} , connection={}", key, timeoutSeconds, connection);
                return supplier.get();
            } finally {
                log.info("Start releaseLock={}, connection={}", key, connection);
                releaseLock(connection, key);
                log.info("Success releaseLock={}, connection={}", key, connection);
            }
        } catch (RoomescapeException | PaymentException exception) {
            throw exception;
        } catch (SQLException | RuntimeException e) {
            throw new RuntimeException(EXCEPTION_MESSAGE, e);
        }
    }

    private void getLock(Connection connection,
                         String key,
                         int timeoutSeconds) throws SQLException {

        try (PreparedStatement preparedStatement = connection.prepareStatement(GET_LOCK)) {
            preparedStatement.setString(1, key);
            preparedStatement.setInt(2, timeoutSeconds);
            checkResultSet(key, preparedStatement, "GetLock_");
        }
    }

    private void releaseLock(Connection connection,
                             String key) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(RELEASE_LOCK)) {
            preparedStatement.setString(1, key);
            checkResultSet(key, preparedStatement, "ReleaseLock_");
        }
    }

    private void checkResultSet(String key,
                                PreparedStatement preparedStatement,
                                String type) throws SQLException {
        try (ResultSet resultSet = preparedStatement.executeQuery()) {
            if (!resultSet.next()) {
                log.error("결과 값이 없습니다. type = {}, key = {}, connection = {}", type, key, preparedStatement.getConnection());
                throw new RuntimeException(EXCEPTION_MESSAGE);
            }
            int result = resultSet.getInt(1);
            if (result != 1) {
                log.error("GET_LOCK 쿼리 결과 값이 1이 아닙니다. type = {}, result = {} key = {}, connection = {}", type, result, key, preparedStatement.getConnection());
                throw new RuntimeException(EXCEPTION_MESSAGE);
            }
        }
    }
}
