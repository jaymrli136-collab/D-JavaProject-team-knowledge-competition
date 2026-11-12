const state = {
  user: 'u_alice',
  projects: [],
  selected: new Set(),
  sessionId: null,
  lastResponse: null
};

const $ = (selector) => document.querySelector(selector);
const userSelect = $('#user-select');
const projectList = $('#project-list');
const ingestProject = $('#ingest-project');
const chatLog = $('#chat-log');
const scopePill = $('#scope-pill');
const questionInput = $('#question-input');
const askButton = $('#ask-button');
const evidenceList = $('#evidence-list');
const traceList = $('#trace-list');
const runBadge = $('#run-badge');

function init() {
  const today = new Date().toISOString().slice(0, 10);
  $('#ingest-date').value = today;
  loadProjects();
  loadDemoQuestions();
  bindEvents();
}

function bindEvents() {
  userSelect.addEventListener('change', () => {
    state.user = userSelect.value;
    state.selected.clear();
    state.sessionId = null;
    resetConversation();
    loadProjects();
  });
  $('#refresh-button').addEventListener('click', loadProjects);
  $('#select-all-button').addEventListener('click', () => {
    state.selected = new Set(state.projects.map(item => item.id));
    renderProjects();
  });
  $('#ask-form').addEventListener('submit', askQuestion);
  questionInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      $('#ask-form').requestSubmit();
    }
  });
  $('#ingest-form').addEventListener('submit', ingestDocument);
  $('#evaluation-button').addEventListener('click', runEvaluation);
  document.querySelectorAll('.tab').forEach(button => {
    button.addEventListener('click', () => switchTab(button.dataset.tab));
  });
}

async function api(path, options = {}) {
  const headers = Object.assign({
    'Content-Type': 'application/json',
    'X-User-Id': state.user
  }, options.headers || {});
  const response = await fetch(path, Object.assign({}, options, { headers }));
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(data.message || '请求失败');
    error.status = response.status;
    error.code = data.code;
    throw error;
  }
  return data;
}

async function loadProjects() {
  try {
    state.projects = await api('/api/projects');
    state.selected = new Set(state.projects.map(item => item.id));
    renderProjects();
    renderIngestProjects();
    updateScopePill();
  } catch (error) {
    showToast(error.message, true);
  }
}

function renderProjects() {
  projectList.replaceChildren();
  state.projects.forEach(project => {
    const label = document.createElement('label');
    label.className = 'project-item';
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = state.selected.has(project.id);
    checkbox.addEventListener('change', () => {
      if (checkbox.checked) state.selected.add(project.id);
      else state.selected.delete(project.id);
      updateScopePill();
    });
    const content = document.createElement('span');
    const name = document.createElement('span');
    name.className = 'project-name';
    name.textContent = project.name;
    const meta = document.createElement('span');
    meta.className = 'project-meta';
    meta.textContent = project.documentCount + ' 份文档 · ' + project.decisionCount + ' 项决策';
    content.append(name, meta);
    label.append(checkbox, content);
    projectList.append(label);
  });
  if (!state.projects.length) {
    projectList.textContent = '当前用户没有可访问项目。';
  }
  updateScopePill();
}

function renderIngestProjects() {
  ingestProject.replaceChildren();
  state.projects.forEach(project => {
    const option = document.createElement('option');
    option.value = project.id;
    option.textContent = project.name;
    ingestProject.append(option);
  });
}

function updateScopePill() {
  const count = state.selected.size;
  scopePill.textContent = count ? '已选 ' + count + ' 个项目' : '将沿用会话范围';
  scopePill.classList.toggle('active', count > 0);
}

async function loadDemoQuestions() {
  try {
    const prompts = await api('/api/demo/questions');
    const container = $('#demo-prompts');
    container.replaceChildren();
    prompts.forEach(prompt => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'prompt-button';
      const title = document.createElement('strong');
      title.textContent = prompt.label;
      const text = document.createElement('span');
      text.textContent = prompt.question;
      button.append(title, text);
      button.addEventListener('click', () => {
        questionInput.value = prompt.question;
        state.selected = new Set(prompt.projectIds || []);
        renderProjects();
        questionInput.focus();
      });
      container.append(button);
    });
  } catch (error) {
    showToast(error.message, true);
  }
}

async function askQuestion(event) {
  event.preventDefault();
  const question = questionInput.value.trim();
  if (!question) return;
  appendUserMessage(question);
  questionInput.value = '';
  askButton.disabled = true;
  askButton.textContent = '正在检索';
  try {
    const response = await api('/api/agent/ask', {
      method: 'POST',
      body: JSON.stringify({
        question,
        projectIds: Array.from(state.selected),
        sessionId: state.sessionId
      })
    });
    state.sessionId = response.sessionId;
    state.lastResponse = response;
    appendAgentResponse(response);
    renderEvidence(response.evidence || []);
    renderTrace(response.trace || []);
    runBadge.textContent = response.runId;
  } catch (error) {
    appendError(error);
    renderEvidence([]);
    renderTrace([]);
    runBadge.textContent = error.code || 'ERROR';
  } finally {
    askButton.disabled = false;
    askButton.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg>发送';
  }
}

function appendUserMessage(question) {
  const article = document.createElement('article');
  article.className = 'message message-user';
  const label = document.createElement('div');
  label.className = 'message-label';
  label.textContent = '你';
  const text = document.createElement('div');
  text.className = 'answer-text';
  text.textContent = question;
  article.append(label, text);
  chatLog.append(article);
  article.scrollIntoView({ behavior: 'smooth', block: 'end' });
}

function appendAgentResponse(response) {
  const article = document.createElement('article');
  article.className = 'message message-agent';
  const label = document.createElement('div');
  label.className = 'message-label';
  label.textContent = 'Project Atlas';
  const text = document.createElement('div');
  text.className = 'answer-text';
  text.textContent = response.answer;
  const meta = document.createElement('div');
  meta.className = 'answer-meta';
  const status = document.createElement('span');
  status.className = 'badge' + (response.status === 'ABSTAINED' ? ' warn' : '');
  status.textContent = response.status === 'ANSWERED' ? '有证据回答' : '证据不足，已拒答';
  const confidence = document.createElement('span');
  confidence.textContent = '置信度 ' + Math.round((response.confidence || 0) * 100) + '%';
  const citations = document.createElement('span');
  citations.textContent = '引用 ' + (response.citations || []).length + ' 条';
  meta.append(status, confidence, citations);
  article.append(label, text, meta);
  chatLog.append(article);
  article.scrollIntoView({ behavior: 'smooth', block: 'end' });
}

function appendError(error) {
  const article = document.createElement('article');
  article.className = 'message message-agent';
  const label = document.createElement('div');
  label.className = 'message-label';
  label.textContent = '安全拦截';
  const text = document.createElement('div');
  text.className = 'answer-text';
  text.textContent = error.status === 403
    ? '当前身份无权访问问题涉及的项目。为保护知识边界，系统没有返回任何项目文档或片段。'
    : error.message;
  const meta = document.createElement('div');
  meta.className = 'answer-meta';
  const badge = document.createElement('span');
  badge.className = 'badge danger';
  badge.textContent = error.code || 'ERROR';
  meta.append(badge);
  article.append(label, text, meta);
  chatLog.append(article);
}

function renderEvidence(evidence) {
  evidenceList.replaceChildren();
  evidenceList.classList.remove('empty-state');
  if (!evidence.length) {
    evidenceList.classList.add('empty-state');
    evidenceList.textContent = '本次回答没有可展示证据。';
    return;
  }
  evidence.forEach(item => {
    const card = document.createElement('article');
    card.className = 'evidence-card';
    const header = document.createElement('header');
    const title = document.createElement('strong');
    title.textContent = '[' + item.evidenceId + '] ' + item.projectName;
    const score = document.createElement('span');
    score.className = 'mono muted';
    score.textContent = item.score.toFixed(3);
    header.append(title, score);
    const body = document.createElement('p');
    body.textContent = item.snippet;
    const meta = document.createElement('div');
    meta.className = 'evidence-meta';
    [item.documentTitle, item.headingPath, item.effectiveDate].forEach(value => {
      const span = document.createElement('span');
      span.textContent = value;
      meta.append(span);
    });
    card.append(header, body, meta);
    evidenceList.append(card);
  });
}

function renderTrace(trace) {
  traceList.replaceChildren();
  trace.forEach(item => {
    const wrap = document.createElement('div');
    wrap.className = 'trace-item';
    const title = document.createElement('strong');
    title.textContent = item.stage + ' · ' + item.status + ' · ' + item.durationMs + 'ms';
    const detail = document.createElement('p');
    detail.textContent = item.detail;
    wrap.append(title, detail);
    traceList.append(wrap);
  });
  if (!trace.length) traceList.textContent = '本次请求没有执行轨迹。';
}

function switchTab(tab) {
  document.querySelectorAll('.tab').forEach(button => button.classList.toggle('active', button.dataset.tab === tab));
  evidenceList.classList.toggle('hidden', tab !== 'evidence');
  traceList.classList.toggle('hidden', tab !== 'trace');
}

async function runEvaluation() {
  const button = $('#evaluation-button');
  button.disabled = true;
  try {
    const report = await api('/api/evaluations/run', { method: 'POST', body: '{}' });
    const metrics = $('#evaluation-metrics').children;
    metrics[0].querySelector('strong').textContent = Math.round(report.passRate * 100) + '%';
    metrics[1].querySelector('strong').textContent = Math.round(report.citationCoverage * 100) + '%';
    metrics[2].querySelector('strong').textContent = Math.round(report.abstentionAccuracy * 100) + '%';
    metrics[3].querySelector('strong').textContent = String(report.permissionViolations);
    const list = $('#evaluation-cases');
    list.replaceChildren();
    list.classList.remove('empty-state');
    report.cases.forEach(item => {
      const card = document.createElement('article');
      card.className = 'eval-case ' + (item.passed ? 'pass' : 'fail');
      const title = document.createElement('strong');
      title.textContent = item.id + ' · ' + (item.passed ? '通过' : '失败');
      const status = document.createElement('p');
      status.textContent = '预期 ' + item.expected + ' / 实际 ' + item.actualStatus;
      const failure = document.createElement('p');
      failure.textContent = item.failures.length ? item.failures.join('；') : item.question;
      card.append(title, status, failure);
      list.append(card);
    });
  } catch (error) {
    showToast(error.message, true);
  } finally {
    button.disabled = false;
  }
}

async function ingestDocument(event) {
  event.preventDefault();
  const projectId = ingestProject.value;
  if (!projectId) {
    showToast('请选择目标项目', true);
    return;
  }
  const body = {
    title: $('#ingest-title').value.trim(),
    content: $('#ingest-content').value,
    sourceUri: $('#ingest-source').value.trim(),
    effectiveDate: $('#ingest-date').value,
    version: 1,
    tags: ['manual-entry']
  };
  try {
    await api('/api/projects/' + encodeURIComponent(projectId) + '/documents', {
      method: 'POST',
      body: JSON.stringify(body)
    });
    showToast('文档已录入，可立即参与检索');
    $('#ingest-title').value = '';
    $('#ingest-content').value = '';
    await loadProjects();
  } catch (error) {
    showToast(error.message, true);
  }
}

function resetConversation() {
  chatLog.replaceChildren();
  const welcome = document.createElement('article');
  welcome.className = 'welcome';
  const title = document.createElement('h2');
  title.textContent = '当前身份已切换';
  const text = document.createElement('p');
  text.textContent = '选择可访问项目后开始提问。未授权项目不会进入检索范围。';
  welcome.append(title, text);
  chatLog.append(welcome);
  evidenceList.classList.add('empty-state');
  evidenceList.textContent = '提问后在这里查看项目、文档、章节与有效期。';
  traceList.replaceChildren();
  runBadge.textContent = '尚无运行';
}

function showToast(message, error = false) {
  const toast = $('#toast');
  toast.textContent = message;
  toast.style.background = error ? '#7f2f2a' : '#10252b';
  toast.classList.add('show');
  clearTimeout(showToast.timer);
  showToast.timer = setTimeout(() => toast.classList.remove('show'), 3200);
}

init();
