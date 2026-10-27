// 사용자 정보를 어떻게 관리하고, 비밀번호가 맞는지 어떻게 확인하는지를 보여줌. 
package edu.classroom.board;
// 현재 코드는 SQLite에서 사용자를 조회하는 방식이 아니라, 프로그램이 실행될 때 테스트 계정 2개를 메모리에 생성하는 방식. 
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component // Sprint에게 이 클래스를 관리 대상으로 등록하라는 의미. Spring이 User객체를 생성하고 관리하기 때문에, 다른 클래스에서 의존성 주입을 통해 사용할 수 있음.  
public class Users {
  // record: 데이터를 담기 위한 클래스를 간결하게 작성하는 java 문법, id(), name(), email() 같은 접근 메서드를 자동으로 제공, 따라서 user.id()처럼 값을 가져올 수 있음. 
  // PublicUser: 외부에 전달할 사용자 정보: ID, 이름, 이메일
  public record PublicUser(long id, String name, String email) {}
  // Account: 내부 계정 정보: PublicUser: 사용자에게 공개할 정보, salt: 비밀번호 해시에 사용하는 무작위 데이터, 비밀번호 해시 함수로 처리한 결과
  private record Account(PublicUser user, byte[] salt, byte[] hash) {}

  // 테스트 계정 생성
  // accounts: accout 객체들을 저장하는 리스트
  private final List<Account> accounts = new ArrayList<>();

  public Users() {
    // Alice와 Bob계정 생성. 각 계정에 16바이트짜라 salt를 무작위로 생성. 테스트 비밀본호를 salt와 함께 해시 처리. 사용자 정보, salt , 해시를 Accout 객체로 묶어 리스트에 넣음. 
    for (int i = 0; i < 2; i++) {
      String name = i == 0 ? "Alice" : "Bob";
      byte[] salt = new byte[16];
      new SecureRandom().nextBytes(salt);
      accounts.add(
          new Account(
              new PublicUser(i + 1, name, name.toLowerCase() + "@class.test"),
              salt,
              hash("Classroom!2026", salt)));
    }
  }
// 비밀번호를 해시 처리하는 역할. 비밀본호를 평문 그대로 저장하는 대신, PBKDF2라는 키 유도 알고리즘을 사용해 해시 결과를 생성. 
  private byte[] hash(String password, byte[] salt) {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210000, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    } finally {
      spec.clearPassword();
    }
  }
  // 사용자 ID로 조회하기, ID에 해당하는 사용자를 찾아 PublicUser로 반환.
  public PublicUser find(long id) {
    return accounts.stream()
        .filter(a -> a.user.id() == id)
        .map(Account::user)
        .findFirst()
        .orElse(null); // 일치하는 사용자가 없으면 null 반환
  }
// 로그인 정보 검증. 
// 1단계: 이메일이 일치하는 계정을 찾는다.
// 2단계: 계정이 있으면 그 계정을 사용하고, 없으면 첫 번째 계정을 비교용으로 사용한다.
// 3단계: 입력받은 비밀번호를 해당 계정의 salt로 해시 처리한다. 
// 4단계: 계산한 해시와 저장된 해시를 MessageDigest.isEqual()로 비교한다.
// 5단계: 이메일과 비밀본호가 모두 맞으면 PublicUser를 반환하고, 그렇지 않으면 null을 반환한다. 
  public PublicUser verify(String email, String password) {
    Account found =
        accounts.stream().filter(a -> a.user.email().equals(email)).findFirst().orElse(null);
    Account reference = found == null ? accounts.get(0) : found;
    boolean ok = MessageDigest.isEqual(hash(password, reference.salt), reference.hash);
    return found != null && ok ? found.user : null;
  }
}

// 로그인 요청 -> Users.verify(): 사용자 정보를 찾고 비밀번호를 검증 -> AuthService.issueToken(): 검증된 사용자에게 JWT 발급 -> 로그인 응답: 사용자 정보와 JWT 전달
