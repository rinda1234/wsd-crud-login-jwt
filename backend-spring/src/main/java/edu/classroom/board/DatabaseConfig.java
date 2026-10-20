package edu.classroom.board;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
// spring boot와 SQLite 파일을 연결
@Configuration //spring의 설정을 담당
public class DatabaseConfig {
  @Bean // 메서드가 반환하는 객체를 Spring이 관리하도록 등록
  public DataSource dataSource(@Value("${app.db.file}") String dbFile) throws IOException { // 설정에서 app.db.file이라는 값을 읽어 dbfile 변수에 넣음. 
    Path file = Path.of(dbFile).toAbsolutePath().normalize(); // 경로 처리
    Files.createDirectories(file.getParent());// 파일을 저장할 폴더 준비
    var source = new DriverManagerDataSource(); // SQLite 연결을 제공할 객체 생성
    source.setDriverClassName("org.sqlite.JDBC"); // SQLite JDBC 드라이버 지정
    source.setUrl("jdbc:sqlite:" + file); // SQLite 파일 경로 지정
    return source; // 설정한 객체를  Spring에 반환 
  }
}
