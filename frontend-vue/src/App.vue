<script setup>
import { ref, onMounted } from "vue";
import { api, login, getSession, clearSession, canEdit } from "./api";
const posts = ref([]),
  selected = ref(null),
  title = ref(""),
  content = ref(""),
  editing = ref(null);
const email = ref("alice@class.test"),
  password = ref("Classroom!2026"),
  user = ref(null),
  busy = ref(false),
  message = ref("게시판을 불러오는 중입니다.");
const refresh = async () => {
  posts.value = await api.list();
};
// 요청시작
async function run(action, success) {
  busy.value = true; // 중복요청방지
  try {
    await action();// API 요청 실행
    message.value = success;// 성공
  } catch (e) {// 실패
    if(e.status === 400){
      message.value = e.message || "입력값을 확인해 주세요.";
      // 입력값은 유지
    }
    else if(e.status === 401){
      // 로그인 상태 해제
      user.value = null;
      if(e.code === "INVALID_CREDENTIALS"){
        message.value = "이메일 또는 비밀번호가 올바르지 않습니다.";
      }
      else if(e.code === "TOKEN_REQUIRED"){
        message.value = "로그인이 필요합니다. 다시 로그인해 주세요.";
      }
      else if(e.code === "INVALID_TOKEN"){
        message.value = "인증 정보가 올바르지 않습니다. 다시 로그인해 주세요.";
      }
      else if(e.code === "TOKEN_EXPIRED"){
        message.value = e.message || "로그인이 만료되었습니다. 다시 로그인해 주세요.";
      }
    }
    else if(e.status === 403){
      // 권한 오류: 로그인 상태는 유지
      message.value = "이 작업을 수행할 권한이 없습니다.";
    }
    else if(e.status === 404){
      // 게시글이 없으면 상세 화면을 비우고 목록 갱신
      message.value = "요청한 게시글을 찾을 수 없습니다.";
      selected.value = null;
      editing.value = null;

      try{
        await refresh();
      } catch{
        //목록 갱신 실패는 별도로 성공 처리하지 않음
      }
      
    }
    else {//위에서 처리하지 않은 별도의 오류
      message.value = e.message || "요청 처리 중 오류가 발생했습니다.";
    }
    
  } finally {//마무리, 로그인  상태 갱신, busy 해제
    user.value = getSession().user;
    busy.value = false;
  }
}
onMounted(() => run(refresh, "게시판이 준비되었습니다."));
function reset() {
  title.value = "";
  content.value = "";
  editing.value = null;
}
function save() {
  run(async () => {
    const input = { title: title.value, content: content.value };
    selected.value = editing.value
      ? await api.update(editing.value, input)
      : await api.create(input);
    reset();
    await refresh();
  }, "게시글을 저장했습니다.");
}
function edit() {
  editing.value = selected.value.id;
  title.value = selected.value.title;
  content.value = selected.value.content;
}
function remove() {
  if (confirm("게시글을 삭제할까요?"))
    run(async () => {
      await api.remove(selected.value.id);
      selected.value = null;
      reset();
      await refresh();
    }, "게시글을 삭제했습니다.");
}
function logout() {
  clearSession();
  user.value = null;
  message.value = "이 탭의 로그인 상태를 해제했습니다.";
}
function me() {
  run(async () => {
    const u = await api.me();
    alert(`${u.name} (${u.email})`);
  }, "내 정보 조회를 완료했습니다.");
}
</script>
<template>
  <main>
    <header>
      <span class="eyebrow">TODAY'S SPENDING DIARY</span>
      <h1>💸 오늘의 거지방</h1>
      <p class="muted">
        오늘 쓴 돈을 솔직하게 고백하고, 내일은 조금 더 아껴 보자.
      </p>
    </header>
    <p class="banner">
      🪙 소비는 자유지만 통장은 솔직합니다.
      오늘 무엇에 돈을 썼는지 기록하고 나의 소비 습관을 돌아보세요.
      본인이 작성한 게시글만 수정하거나 삭제할 수 있습니다.
    </p>
    <div role="status" class="message">{{ message }}</div>
    <div class="grid">
      <aside>
        <section class="card">
          <h2>🔑 거지방 입장</h2>
          <p>{{ user ? `${user.name}님, 오늘도 절약해 봐요!` : "로그인하고 소비 일기를 남겨 보세요." }}</p>
          <form
            @submit.prevent="
              run(() => login(email, password), '로그인했습니다.')
            "
          >
            <label>이메일<input type="email" v-model="email" required /></label
            ><label
              >비밀번호<input type="password" v-model="password" required
            /></label>
            <div class="actions">
              <button :disabled="busy">로그인</button
              ><button type="button" class="secondary" @click="logout">
                로그아웃
              </button>
            </div>
          </form>
          <p>
            <small
              >테스트 계정: alice@class.test / bob@class.test<br />공통
              비밀번호: Classroom!2026</small
            >
          </p>
          <button class="secondary" :disabled="busy" @click="me">
            내 정보 조회
          </button>
        </section>
        <section class="card">
          <h2>{{ editing ? "✏️ 소비 고백 수정" : "💸 오늘의 소비 고백" }}</h2>
          <form @submit.prevent="save">
            <label>
              무엇에 돈을 썼어?
              <input maxlength="15000" v-model="title" required />
            </label>
            <label>
              소비 이유와 반성문
              <textarea maxlength="5000" v-model="content" required></textarea>
            </label>
            <div class="actions">
              <button :disabled="busy">
                {{ editing ? "수정 저장" : "등록" }}</button
              ><button type="button" class="secondary" @click="reset">
                입력 초기화
              </button>
            </div>
          </form>
        </section>
      </aside>
      <div>
        <section class="card">
          <h2>
            📒 거지들의 소비 일기 <small>{{ posts.length }}개</small>
          </h2>
          <p v-if="!posts.length">아직 소비 고백이 없어요. 첫 기록을 남겨 보세요!</p>
          <article class="post" v-for="p in posts" :key="p.id">
            <button
              class="post-title"
              :disabled="busy"
              @click="
                run(
                  async () => (selected = await api.detail(p.id)),
                  '게시글을 불러왔습니다.',
                )
              "
            >
              {{ p.title }}
            </button>
            <p>
              <small
                >{{ p.authorName }} ·
                {{ new Date(p.createdAt).toLocaleString() }}</small
              >
            </p>
          </article>
        </section>
        <section class="card">
          <h2>🧾 소비 고백 상세</h2>
          <template v-if="selected"
            ><h3>{{ selected.title }}</h3>
            <p class="content">{{ selected.content }}</p>
            <p>
              <small>작성자 {{ selected.authorName }}</small>
            </p>
            <div class="actions" v-if="canEdit(selected)">
              <button class="secondary" @click="edit">수정</button
              ><button class="danger" :disabled="busy" @click="remove">
                삭제
              </button>
            </div></template
          >
          <p class="muted" v-else>목록에서 제목을 선택하세요.</p>
        </section>
      </div>
    </div>
  </main>
</template>
