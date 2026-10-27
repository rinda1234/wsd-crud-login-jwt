package edu.classroom.board;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// 실제 요청이 들어왔을 때 각 기능을 연결하는 중심 역할.
@RestController
@RequestMapping("/api")
public class BoardController {
  private final PostStore store;// 게시글 조회/등록/수정/삭제 처리
  private final AuthService auth;// JWT 발급/검증 및 작성자 권한 검사
  private final Users users;// 사용자 조회 및 로그인 정보 검증

  // 다른 클래스와 연결하는 부분
  @Value("${app.jwt.ttl}")// 설정 파일에서 app.jwt.ttl 값을 읽어 ttl에 넣어 줌. 
  private int ttl; // JWT 만료 시간 관련 설정값

  public BoardController(PostStore store, AuthService auth, Users users) { // spring이 해당 객체들을 주입해주기 때문에 컨트롤러가 직접 객체를 생성할 필요가 없음. 
    this.store = store; 
    this.auth = auth; 
    this.users = users; 
  }

  // JSON 입력값 검증: 요청으로 들어온 JSON 데이터가 올바른지 확인. 
  private Map<String, String> fields(Map<String, Object> body, Map<String, Integer> limits) {
    if (body == null || !limits.keySet().containsAll(body.keySet()))
      throw new ApiError(400, "BAD_INPUT", "허용된 JSON 필드만 보내세요.");
    Map<String, String> out = new HashMap<>();
    for (var e : limits.entrySet()) {
      Object v = body.get(e.getKey());
      if (!(v instanceof String)) throw new ApiError(400, "BAD_INPUT", "문자열을 입력하세요.");
      String s = (String) v, t = s.strip();
      if (t.isEmpty() || t.codePointCount(0, t.length()) > e.getValue())
        throw new ApiError(400, "BAD_INPUT", e.getKey() + " 입력을 확인하세요.");
      out.put(e.getKey(), e.getKey().equals("password") ? s : t);
    }
    return out;
  }

  // 게시글 ID 검증: URL에 전달된 게시글 ID가 유효한지 검사
  private long postId(String value) {
    try {
      if (!value.matches("[1-9][0-9]*")) throw new NumberFormatException();
      long id = Long.parseLong(value);
      if (id > 9007199254740991L) throw new NumberFormatException();
      return id;
    } catch (NumberFormatException e) {
      throw new ApiError(400, "BAD_INPUT", "ID는 양의 정수여야 합니다.");
    }
  }

  // 게시글 존재 여부 확인 
  private PostStore.Post existing(long id) {
    var p = store.get(id);
    if (p == null) throw new ApiError(404, "NOT_FOUND", "게시글이 없습니다.");
    return p;
  }

  // 서버 상태 확인: 요청이 들어오면 서버 상태를 JSON으로 반환
  @GetMapping("/health")
  public Object health() {
    return Map.of("status", "ok", "stack", "spring");
  }

  // 로그인
  // 성공 응답: 
//   {
//   "accessToken": "발급된_JWT",
//   "expiresIn": 3600,
//   "user": {
//     "id": 1,
//     "name": "Alice",
//     "email": "alice@class.test"
//   }
// }
  @PostMapping("/auth/login")
  public Object login(@RequestBody Map<String, Object> body) {
    var input = fields(body, Map.of("email", 254, "password", 128));
    var user = users.verify(input.get("email"), input.get("password")); // 계정과 비밀번호 확인 
    if (user == null) throw new ApiError(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인하세요."); // 검증에 실패하면 오류 발생
    return Map.of("accessToken", auth.issueToken(user), "expiresIn", ttl, "user", user); // 성공 시 토큰 발급
  }

  // 현재 사용자 확인: 현재 요청을 보낸 사용자가 누구인지 확인
  @GetMapping("/me")
  public Object me(HttpServletRequest req) {
    var user = users.find(auth.currentUserId(req));  // 요청의 JWT를 검증하고 사용자 ID를 반환, 반환한 ID를 조회
    if (user == null) throw new ApiError(401, "INVALID_TOKEN", "사용자를 확인할 수 없습니다.");// 사용자가 존재하지 않으면 401오류
    return user;
  }

  // 게시글 목록과 상세 조회: 게시글 목록 반환
  @GetMapping("/posts")
  public Object list() {
    return store.list();
  }

  // 특정 게시글 조회
  @GetMapping("/posts/{id}")
  public Object get(@PathVariable String id) {
    return existing(postId(id));
  }

  // 게시글 작성
  @PostMapping("/posts")
  public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
    long user = auth.currentUserId(req); // 요청에 포함된 JWT를 검증해 사용자 ID를 얻어 게시글 생성 함수에 전달. 
    var input = fields(body, Map.of("title", 100, "content", 5000));
    var p = store.create(input.get("title"), input.get("content"), user); 
    return ResponseEntity.created(URI.create("/api/posts/" + p.id())).body(p);
  }

  // 게시글 수정: 
  // 실행 순서
  // 1. 요청 사용자의 ID를 확인한다.
  // 2. URL의 게시글 ID를 검증한다.
  // 3. 해당 게시글이 존재하는지 확인한다.
  // 4. 현재 사용자 ID와 게시글 작성자 ID를 비교한다.
  // 5. 요청 본문의 제목과 내용을 검증한다.
  // 6. 게시글을 수정한다.
  @PutMapping("/posts/{id}")
  public Object update(
      @PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
    long user = auth.currentUserId(req), pid = postId(id);
    var p = existing(pid);
    auth.assertOwner(user, p.authorId());
    var input = fields(body, Map.of("title", 100, "content", 5000));
    return store.update(pid, input.get("title"), input.get("content"));
  }

  // 게시글 삭제
  @DeleteMapping("/posts/{id}")
  public ResponseEntity<?> delete(@PathVariable String id, HttpServletRequest req) {
    long user = auth.currentUserId(req), pid = postId(id);
    var p = existing(pid);
    auth.assertOwner(user, p.authorId());
    store.delete(pid);
    return ResponseEntity.noContent().build();
  }
}
