package edu.classroom.board;

import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
// 게시글 데이터를 실제 데이터베이스에 저장하고 조회/수정/삭제하는 역할
// BoardController가 API 요청을 받아 처리하는 곳이라면, PostStore는 그 요청에 맞춰 실제 SQL을 실행하는 곳. 
// 로그인이자 작성자 권한을 검사하지는 않고, 전달받은 값으로 데이터베이스 작업을 수행. 
@Repository// Spring이 이 클래스를 관리하도록 등록, 데이터 접근을 담당.
// 게시글 데이터를 처리하는 클래스
public class PostStore { 
  // 게시글 데이터 구조
  public record Post(
      long id, //번호
      String title, // 제목
      String content, // 본문
      long authorId, //작성자 ID
      String authorName, // 작성자 이름
      String createdAt, //작성 시간
      String updatedAt) {} //마지막 수정 시간 

  // SQL을 실행하고 데이터베이스 결과를 가져옴
  private final JdbcTemplate db;
  // 작성자 ID를 이용해 작성자 이름을 조회
  private final Users users;

  public PostStore(JdbcTemplate db, Users users) {
    this.db = db;
    this.users = users;
  }

  // SQL 결과를 Post 객체로 변환 (데이터 베이스에서 가져온 한 행을 Post 객체로 변환)
  private Post shape(java.sql.ResultSet r, int n) throws java.sql.SQLException {
    long author = r.getLong("author_id");
    return new Post(
        r.getLong("id"),
        r.getString("title"),
        r.getString("content"),
        author,
        users.find(author).name(),
        r.getString("created_at"),
        r.getString("updated_at"));
  }

  // 전체 게시글 조회
  public List<Post> list() {
    return db.query("SELECT * FROM posts ORDER BY id DESC", this::shape);
  }

  // 게시글 한 개 조회
  public Post get(long id) {
    return db.query("SELECT * FROM posts WHERE id=?", this::shape, id).stream()
        .findFirst()
        .orElse(null);
  }

  // 게시글 작성
  public Post create(String title, String content, long author) {
    String now = Instant.now().toString();
    var key = new GeneratedKeyHolder();
    db.update(
        c -> {
          var s =
              c.prepareStatement(
                  "INSERT INTO posts(title,content,author_id,created_at,updated_at)"
                      + " VALUES(?,?,?,?,?)",
                  Statement.RETURN_GENERATED_KEYS);
          s.setString(1, title);
          s.setString(2, content);
          s.setLong(3, author);
          s.setString(4, now);
          s.setString(5, now);
          return s;
        },
        key);
    return get(key.getKey().longValue());
  }

  // 게시글 수정
  public Post update(long id, String title, String content) {
    db.update(
        "UPDATE posts SET title=?,content=?,updated_at=? WHERE id=?",
        title,
        content,
        Instant.now().toString(),
        id);
    return get(id);
  }

  // 게시글 삭제
  public void delete(long id) {
    db.update("DELETE FROM posts WHERE id=?", id);
  }
}
