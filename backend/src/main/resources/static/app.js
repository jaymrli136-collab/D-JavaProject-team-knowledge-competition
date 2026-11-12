const state = {
  user: 'u_alice',
  projects: [],
  selected: new Set(),
  sessionId: null,
  lastResponse: null,
  memories: [],
  memoryDraft: null,
  lastQuestion: ''
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
const memoryList = $('#memory-list');
const memoryDialog = $('#memory-dialog');

function init() {
  const today = new Date().toISOString().slice(0, 10);
  $('#ingest-date').value = today;
  loadProjects();
  loadMemories();
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
    loadMemories();
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
  $('#memory-form').addEventListener('submit', submitMemory);
  $('#memory-close').addEventListener('click', () => memoryDialog.close());
  $('#memory-cancel').addEventListener('click', () => memoryDialog.close());
  $('#memory-project').addEventListener('change', renderMemoryDraftEvidence);
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
  state.lastQuestion = question;
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
  if (response.status === 'ANSWERED' && (response.citations || []).length) {
    const rememberButton = document.createElement('button');
    rememberButton.type = 'button';
    rememberButton.className = 'small-button';
    rememberButton.textContent = '沉淀候选记忆';
    rememberButton.addEventListener('click', () => openMemoryDialog(response));
    meta.append(rememberButton);
  }
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
  memoryList.classList.toggle('hidden', tab !== 'memory');
  if (tab === 'memory') loadMemories();
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


async function loadMemories() {
  try {
    state.memories = await api('/api/memories');
    renderMemories();
  } catch (error) {
    showToast(error.message, true);
  }
}

function renderMemories() {
  memoryList.replaceChildren();
  if (!state.memories.length) {
    memoryList.classList.add('empty-state');
    memoryList.textContent = '暂无项目记忆。回答完成后可沉淀候选记忆，由项目成员确认。';
    return;
  }
  memoryList.classList.remove('empty-state');
  state.memories.forEach(memory => {
    const card = document.createElement('article');
    card.className = 'memory-card';
    const header = document.createElement('header');
    const title = document.createElement('h4');
    title.textContent = memory.title;
    const badge = document.createElement('span');
    badge.className = 'badge' + (memory.status === 'PROPOSED' ? ' warn' : memory.status === 'RETIRED' ? ' danger' : '');
    badge.textContent = memory.status === 'PROPOSED' ? '待确认' : memory.status === 'ACTIVE' ? '已确认' : '已撤回';
    header.append(title, badge);
    const meta = document.createElement('div');
    meta.className = 'evidence-meta';
    [memory.projectName, memoryTypeLabel(memory.type), '来源 ' + memory.sourceEvidenceIds.join(',')].forEach(text => {
      const span = document.createElement('span');
      span.textContent = text;
      meta.append(span);
    });
    const body = document.createElement('p');
    body.textContent = memory.content;
    const actions = document.createElement('div');
    actions.className = 'memory-actions';
    if (memory.status === 'PROPOSED') {
      const confirm = document.createElement('button');
      confirm.type = 'button';
      confirm.className = 'small-button';
      confirm.textContent = '确认启用';
      confirm.addEventListener('click', () => updateMemory(memory.id, 'confirm'));
      actions.append(confirm);
    }
    if (memory.status !== 'RETIRED') {
      const retire = document.createElement('button');
      retire.type = 'button';
      retire.className = 'small-button danger';
      retire.textContent = '撤回';
      retire.addEventListener('click', () => updateMemory(memory.id, 'retire'));
      actions.append(retire);
    }
    card.append(header, meta, body, actions);
    memoryList.append(card);
  });
}

async function updateMemory(memoryId, action) {
  try {
    await api('/api/memories/' + encodeURIComponent(memoryId) + '/' + action, { method: 'POST', body: '{}' });
    showToast(action === 'confirm' ? '记忆已确认启用' : '记忆已撤回');
    await loadMemories();
  } catch (error) {
    showToast(error.message, true);
  }
}

function openMemoryDialog(response) {
  state.memoryDraft = response;
  const projectIds = Array.from(new Set((response.citations || []).map(item => item.projectId)));
  const projectSelect = $('#memory-project');
  projectSelect.replaceChildren();
  projectIds.forEach(projectId => {
    const citation = response.citations.find(item => item.projectId === projectId);
    const option = document.createElement('option');
    option.value = projectId;
    option.textContent = citation ? citation.projectName : projectId;
    projectSelect.append(option);
  });
  $('#memory-title').value = truncateText('回答事实：' + state.lastQuestion, 80);
  $('#memory-content').value = truncateText(response.answer, 500);
  $('#memory-type').value = 'DECISION';
  renderMemoryDraftEvidence();
  memoryDialog.showModal();
}

function renderMemoryDraftEvidence() {
  const response = state.memoryDraft;
  const projectId = $('#memory-project').value;
  const container = $('#memory-evidence');
  container.replaceChildren();
  if (!response) return;
  (response.citations || []).filter(item => item.projectId === projectId).forEach(item => {
    const tag = document.createElement('span');
    tag.textContent = '[' + item.evidenceId + '] ' + item.documentTitle + ' / ' + item.headingPath;
    container.append(tag);
  });
}

async function submitMemory(event) {
  event.preventDefault();
  const response = state.memoryDraft;
  const projectId = $('#memory-project').value;
  const evidenceIds = (response?.citations || [])
      .filter(item => item.projectId === projectId)
      .map(item => item.evidenceId);
  if (!evidenceIds.length) {
    showToast('当前项目没有可绑定的原文证据', true);
    return;
  }
  try {
    await api('/api/memories/propose', {
      method: 'POST',
      body: JSON.stringify({
        projectId,
        type: $('#memory-type').value,
        title: $('#memory-title').value.trim(),
        content: $('#memory-content').value.trim(),
        sourceRunId: response.runId,
        evidenceIds
      })
    });
    memoryDialog.close();
    showToast('候选记忆已提交，等待项目成员确认');
    await loadMemories();
    switchTab('memory');
  } catch (error) {
    showToast(error.message, true);
  }
}

function memoryTypeLabel(type) {
  return ({
    DECISION: '决策',
    CONSTRAINT: '约束',
    RISK: '风险',
    OWNER: '负责人',
    TERM: '术语',
    EXPERIENCE: '经验'
  })[type] || type;
}

function truncateText(value, max) {
  if (!value) return '';
  return value.length <= max ? value : value.slice(0, max) + '...';
}

init();
