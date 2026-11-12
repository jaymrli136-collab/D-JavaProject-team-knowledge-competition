const state = {
  user: 'u_alice',
  projects: [],
  selected: new Set(),
  sessionId: null,
  lastResponse: null,
  memories: [],
  gaps: [],
  documents: [],
  memoryDraft: null,
  lastQuestion: '',
  activeStreamController: null,
  ingestFile: null,
  projectArchive: null
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
const gapList = $('#gap-list');
const runHistory = $('#run-history');
const documentList = $('#document-list');
const memoryDialog = $('#memory-dialog');
const sectionDialog = $('#section-dialog');
const projectDialog = $('#project-dialog');

function init() {
  const savedUser = localStorage.getItem('project-atlas-user');
  if (savedUser) {
    state.user = savedUser;
    userSelect.value = savedUser;
  }
  state.sessionId = localStorage.getItem('project-atlas-session');
  const today = new Date().toISOString().slice(0, 10);
  $('#ingest-date').value = today;
  loadProjects();
  loadMemories();
  loadGaps();
  loadRuntimeStatus();
  loadUserDirectory();
  loadEvaluationHistory();
  loadDocuments();
  loadDemoQuestions();
  bindEvents();
}

function bindEvents() {
  userSelect.addEventListener('change', () => {
    state.user = userSelect.value;
    localStorage.setItem('project-atlas-user', state.user);
    state.selected.clear();
    state.sessionId = null;
    resetConversation();
    loadProjects();
    loadMemories();
    loadGaps();
  });
  $('#refresh-button').addEventListener('click', loadProjects);
  $('#reset-session-button').addEventListener('click', resetSession);
  $('#select-all-button').addEventListener('click', () => {
    state.selected = new Set(state.projects.map(item => item.id));
    renderProjects();
    loadHealthOverview();
  });
  $('#ask-form').addEventListener('submit', askQuestion);
  $('#preview-button').addEventListener('click', previewRetrieval);
  questionInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      $('#ask-form').requestSubmit();
    }
  });
  $('#ingest-form').addEventListener('submit', ingestDocument);
  $('#ingest-file').addEventListener('change', loadIngestFile);
  $('#ingest-project').addEventListener('change', updateIngestPermission);
  $('#evaluation-button').addEventListener('click', runEvaluation);
  $('#memory-form').addEventListener('submit', submitMemory);
  $('#memory-close').addEventListener('click', () => memoryDialog.close());
  $('#memory-cancel').addEventListener('click', () => memoryDialog.close());
  $('#section-close').addEventListener('click', () => sectionDialog.close());
  $('#create-project-button').addEventListener('click', () => projectDialog.showModal());
  $('#project-close').addEventListener('click', () => projectDialog.close());
  $('#project-cancel').addEventListener('click', () => projectDialog.close());
  $('#project-form').addEventListener('submit', createProject);
  $('#project-archive-file').addEventListener('change', loadProjectArchiveFile);
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
    updateIngestPermission();
    loadHealthOverview();
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
      loadHealthOverview();
      if (!$('#memory-list').classList.contains('hidden')) loadMemories();
      if (!$('#gap-list').classList.contains('hidden')) loadGaps();
      if (!$('#document-list').classList.contains('hidden')) loadDocuments();
      updateIngestPermission();
    });
    const content = document.createElement('span');
    const name = document.createElement('span');
    name.className = 'project-name';
    name.textContent = project.name;
    const meta = document.createElement('span');
    meta.className = 'project-meta';
    const currentRole = project.memberRoles?.[state.user] || (project.ownerId === state.user ? 'OWNER' : 'VIEWER');
    meta.textContent = project.documentCount + ' 份文档 · ' + project.decisionCount + ' 项决策 · '
      + (project.memberIds || []).length + ' 名成员 · ' + currentRole;
    content.append(name, meta);
    if (project.ownerId === state.user) {
      const memberButton = document.createElement('button');
      memberButton.type = 'button';
      memberButton.className = 'small-button project-member-action';
      memberButton.textContent = '成员 ' + (project.memberIds || []).length;
      memberButton.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        manageProjectMembers(project);
      });
      const roleButton = document.createElement('button');
      roleButton.type = 'button';
      roleButton.className = 'small-button project-member-action';
      roleButton.textContent = '角色';
      roleButton.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        manageProjectRole(project);
      });
      const exportButton = document.createElement('button');
      exportButton.type = 'button';
      exportButton.className = 'small-button project-member-action';
      exportButton.textContent = '导出';
      exportButton.addEventListener('click', event => {
        event.preventDefault();
        event.stopPropagation();
        exportProject(project);
      });
      content.append(memberButton, roleButton, exportButton);
    }
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
  askButton.textContent = '正在执行';
  const progress = appendProgressMessage();
  const controller = new AbortController();
  state.activeStreamController = controller;
  try {
    const response = await streamAsk({
      question,
      projectIds: Array.from(state.selected),
      sessionId: state.sessionId
    }, step => updateProgressMessage(progress, step), controller.signal);
    state.sessionId = response.sessionId;
    localStorage.setItem('project-atlas-session', state.sessionId);
    state.lastResponse = response;
    progress.remove();
    appendAgentResponse(response);
    renderEvidence(response.evidence || []);
    renderTrace(response.trace || []);
    runBadge.textContent = response.runId;
    loadRunHistory();
    if (response.status === 'ABSTAINED') loadGaps();
    loadHealthOverview();
  } catch (error) {
    progress.remove();
    if (error.name === 'AbortError') {
      showToast('执行已取消');
      return;
    }
    appendError(error);
    renderEvidence([]);
    renderTrace([]);
    runBadge.textContent = error.code || 'ERROR';
  } finally {
    state.activeStreamController = null;
    askButton.disabled = false;
    askButton.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/></svg>发送';
  }
}

async function streamAsk(payload, onTrace, signal) {
  const response = await fetch('/api/agent/ask/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      'X-User-Id': state.user
    },
    body: JSON.stringify(payload),
    signal
  });
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const error = new Error(data.message || '请求失败');
    error.status = response.status;
    error.code = data.code;
    throw error;
  }
  if (!response.body) throw new Error('浏览器不支持流式响应');
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    buffer = buffer.replace(/\r\n/g, '\n');
    let boundary = buffer.indexOf('\n\n');
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary);
      buffer = buffer.slice(boundary + 2);
      const event = parseSseBlock(block);
      if (event.name === 'trace') onTrace(event.data);
      if (event.name === 'result') return event.data;
      if (event.name === 'error') {
        const error = new Error(event.data.message || '流式执行失败');
        error.status = event.data.code === 'FORBIDDEN' ? 403 : 500;
        error.code = event.data.code;
        throw error;
      }
      boundary = buffer.indexOf('\n\n');
    }
    if (done) break;
  }
  throw new Error('流式响应意外结束');
}

function parseSseBlock(block) {
  let name = 'message';
  const data = [];
  block.split('\n').forEach(line => {
    if (line.startsWith('event:')) name = line.slice(6).trim();
    if (line.startsWith('data:')) data.push(line.slice(5).trim());
  });
  return { name, data: data.length ? JSON.parse(data.join('\n')) : null };
}

function appendProgressMessage() {
  const article = document.createElement('article');
  article.className = 'message message-agent';
  const label = document.createElement('div');
  label.className = 'message-label';
  label.textContent = 'Project Atlas · Execution';
  const status = document.createElement('div');
  status.className = 'stream-status';
  status.textContent = '正在解析项目范围…';
  const trace = document.createElement('div');
  trace.className = 'stream-trace';
  const cancel = document.createElement('button');
  cancel.type = 'button';
  cancel.className = 'small-button danger stream-cancel';
  cancel.textContent = '取消执行';
  cancel.addEventListener('click', () => state.activeStreamController?.abort());
  article.append(label, status, trace, cancel);
  chatLog.append(article);
  article.scrollIntoView({ behavior: 'smooth', block: 'end' });
  return article;
}

function updateProgressMessage(article, step) {
  if (!step) return;
  const labels = {
    scope: '项目范围',
    memory: '记忆召回',
    tool: '工具路由',
    retrieve: '证据检索',
    answer: '生成回答',
    verify: '引用校验',
    gap: '知识缺口'
  };
  const status = article.querySelector('.stream-status');
  status.textContent = (labels[step.stage] || step.stage) + ' · ' + step.status;
  const trace = article.querySelector('.stream-trace');
  const row = document.createElement('div');
  row.textContent = (labels[step.stage] || step.stage) + ' · ' + step.status + ' · ' + step.durationMs + 'ms';
  trace.append(row);
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
  renderAnswerContent(text, response.answer);
  const meta = document.createElement('div');
  meta.className = 'answer-meta';
  const status = document.createElement('span');
  status.className = 'badge' + (response.status === 'ABSTAINED' ? ' warn' : '');
  status.textContent = response.status === 'ANSWERED' ? '有证据回答' : '证据不足，已拒答';
  const confidence = document.createElement('span');
  confidence.textContent = '置信度 ' + Math.round((response.confidence || 0) * 100) + '%';
  const citations = document.createElement('span');
  citations.textContent = '引用 ' + (response.citations || []).length + ' 条';
  const coverage = document.createElement('span');
  coverage.textContent = '引用覆盖 ' + Math.round((response.citationCoverage || 0) * 100) + '%';
  meta.append(status, confidence, citations, coverage);
  if (response.status === 'ANSWERED' && (response.citations || []).length) {
    const rememberButton = document.createElement('button');
    rememberButton.type = 'button';
    rememberButton.className = 'small-button';
    rememberButton.textContent = '沉淀候选记忆';
    rememberButton.addEventListener('click', () => openMemoryDialog(response));
    meta.append(rememberButton);
  }
  const helpful = document.createElement('button');
  helpful.type = 'button';
  helpful.className = 'small-button';
  helpful.textContent = '有帮助';
  helpful.addEventListener('click', () => submitFeedback(response.runId, 'HELPFUL', '', helpful));
  const notHelpful = document.createElement('button');
  notHelpful.type = 'button';
  notHelpful.className = 'small-button danger';
  notHelpful.textContent = '需改进';
  notHelpful.addEventListener('click', () => submitFeedback(response.runId, 'NOT_HELPFUL', '', notHelpful));
  meta.append(helpful, notHelpful);
  article.append(label, text, meta);
  if ((response.memoryHints || []).length) {
    const hint = document.createElement('div');
    hint.className = 'memory-hint-note';
    const title = document.createElement('strong');
    title.textContent = '已应用项目记忆（非事实来源）';
    const list = document.createElement('div');
    list.textContent = response.memoryHints.map(item => item.title + '：' + item.content).join('；');
    hint.append(title, list);
    article.append(hint);
  }
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
    const sourceButton = document.createElement('button');
    sourceButton.type = 'button';
    sourceButton.className = 'small-button';
    sourceButton.textContent = '查看原文';
    sourceButton.addEventListener('click', () => openSection(item));
    meta.append(sourceButton);
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
  runHistory.classList.toggle('hidden', tab !== 'trace');
  memoryList.classList.toggle('hidden', tab !== 'memory');
  gapList.classList.toggle('hidden', tab !== 'gap');
  documentList.classList.toggle('hidden', tab !== 'documents');
  if (tab === 'memory') loadMemories();
  if (tab === 'gap') loadGaps();
  if (tab === 'documents') loadDocuments();
  if (tab === 'trace') loadRunHistory();
}

async function runEvaluation() {
  const button = $('#evaluation-button');
  button.disabled = true;
  try {
    const report = await api('/api/evaluations/run', { method: 'POST', body: '{}' });
    const metrics = $('#evaluation-metrics').children;
    metrics[0].querySelector('strong').textContent = Math.round(report.passRate * 100) + '%';
    metrics[1].querySelector('strong').textContent = Math.round(report.citationCoverage * 100) + '%';
    metrics[2].querySelector('strong').textContent = Math.round(report.evidenceRecall * 100) + '%';
    metrics[3].querySelector('strong').textContent = Math.round(report.abstentionAccuracy * 100) + '%';
    metrics[4].querySelector('strong').textContent = String(report.permissionViolations);
    const list = $('#evaluation-cases');
    list.replaceChildren();
    list.classList.remove('empty-state');
    await loadEvaluationHistory();
    await loadHealthOverview();
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
    tags: ['manual-entry'],
    lineageKey: $('#ingest-lineage').value.trim() || null
  };
  try {
    const file = state.ingestFile;
    if (file && /\.(pdf|docx)$/i.test(file.name)) {
      const form = new FormData();
      form.append('file', file);
      form.append('title', body.title);
      form.append('effectiveDate', body.effectiveDate);
      form.append('version', String(body.version));
      if (body.lineageKey) form.append('lineageKey', body.lineageKey);
      const response = await fetch('/api/projects/' + encodeURIComponent(projectId) + '/documents/upload', {
        method: 'POST',
        headers: { 'X-User-Id': state.user },
        body: form
      });
      if (!response.ok) {
        const errorBody = await response.json().catch(() => ({}));
        throw new Error(errorBody.message || '文档上传失败');
      }
    } else {
      await api('/api/projects/' + encodeURIComponent(projectId) + '/documents', {
        method: 'POST',
        body: JSON.stringify(body)
      });
    }
    showToast('文档已录入，可立即参与检索');
    $('#ingest-title').value = '';
    $('#ingest-content').value = '';
    $('#ingest-lineage').value = '';
    $('#ingest-file').value = '';
    state.ingestFile = null;
    $('#ingest-content').required = true;
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
  title.textContent = '已开启新的知识会话';
  const text = document.createElement('p');
  text.textContent = '选择可访问项目后开始提问。未授权项目不会进入检索范围。';
  const prompts = document.createElement('div');
  prompts.id = 'demo-prompts';
  prompts.className = 'prompt-grid';
  welcome.append(title, text, prompts);
  chatLog.append(welcome);
  evidenceList.classList.add('empty-state');
  evidenceList.textContent = '提问后在这里查看项目、文档、章节与有效期。';
  traceList.replaceChildren();
  runBadge.textContent = '尚无运行';
  loadDemoQuestions();
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
    const projectId = state.selected.size === 1 ? Array.from(state.selected)[0] : null;
    state.memories = await api('/api/memories' + (projectId ? '?projectId=' + encodeURIComponent(projectId) : ''));
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
    await loadHealthOverview();
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


async function loadGaps() {
  try {
    const projectId = state.selected.size === 1 ? Array.from(state.selected)[0] : null;
    state.gaps = await api('/api/gaps' + (projectId ? '?projectId=' + encodeURIComponent(projectId) : ''));
    renderGaps();
  } catch (error) {
    showToast(error.message, true);
  }
}

function renderGaps() {
  gapList.replaceChildren();
  if (!state.gaps.length) {
    gapList.classList.add('empty-state');
    gapList.textContent = '暂无知识缺口。证据不足的拒答会自动记录在这里。';
    return;
  }
  gapList.classList.remove('empty-state');
  const toolbar = document.createElement('div');
  toolbar.className = 'memory-actions';
  const exportButton = document.createElement('button');
  exportButton.type = 'button';
  exportButton.className = 'small-button';
  exportButton.textContent = '导出缺口清单';
  exportButton.addEventListener('click', downloadGapReport);
  toolbar.append(exportButton);
  gapList.append(toolbar);
  state.gaps.forEach(gap => {
    const card = document.createElement('article');
    card.className = 'memory-card';
    const header = document.createElement('header');
    const title = document.createElement('h4');
    title.textContent = gap.question;
    const badge = document.createElement('span');
    badge.className = 'badge' + (gap.status === 'OPEN' || gap.status === 'IN_REVIEW' ? ' warn' : gap.status === 'IGNORED' ? ' danger' : '');
    badge.textContent = gap.status === 'OPEN' ? '待补充' : gap.status === 'IN_REVIEW' ? '待复核' : gap.status === 'RESOLVED' ? '已解决' : '已忽略';
    header.append(title, badge);
    const meta = document.createElement('div');
    meta.className = 'evidence-meta';
    [gap.projectNames.join(' / '), '出现 ' + gap.occurrences + ' 次', formatTime(gap.lastSeenAt)].forEach(text => {
      const span = document.createElement('span');
      span.textContent = text;
      meta.append(span);
    });
    const body = document.createElement('p');
    body.textContent = gap.resolutionNote ? '处理说明：' + gap.resolutionNote
        : gap.candidateDocumentTitle ? '发现候选文档：' + gap.candidateDocumentTitle + '，请项目成员复核。'
        : '缺少可回答问题所需的项目证据。';
    const actions = document.createElement('div');
    actions.className = 'memory-actions';
    if (gap.status === 'OPEN' || gap.status === 'IN_REVIEW') {
      const fill = document.createElement('button');
      fill.type = 'button';
      fill.className = 'small-button';
      fill.textContent = '录入知识';
      fill.addEventListener('click', () => prefillGap(gap));
      actions.append(fill);
      const resolve = document.createElement('button');
      resolve.type = 'button';
      resolve.className = 'small-button';
      resolve.textContent = '标记已解决';
      resolve.addEventListener('click', () => updateGap(gap.id, 'resolve'));
      const ignore = document.createElement('button');
      ignore.type = 'button';
      ignore.className = 'small-button danger';
      ignore.textContent = '忽略';
      ignore.addEventListener('click', () => updateGap(gap.id, 'ignore'));
      actions.append(resolve, ignore);
    }
    card.append(header, meta, body, actions);
    gapList.append(card);
  });
}

async function updateGap(gapId, action) {
  const note = window.prompt(action === 'resolve' ? '补充处理说明（可选）' : '忽略原因（可选）', '') || '';
  try {
    await api('/api/gaps/' + encodeURIComponent(gapId) + '/' + action, {
      method: 'POST',
      body: JSON.stringify({ note })
    });
    showToast(action === 'resolve' ? '知识缺口已标记为已解决' : '知识缺口已忽略');
    await loadGaps();
    await loadHealthOverview();
  } catch (error) {
    showToast(error.message, true);
  }
}

function formatTime(value) {
  if (!value) return '';
  return new Date(value).toLocaleString('zh-CN', { hour12: false });
}


async function openSection(evidence) {
  if (!evidence.documentId || !evidence.sectionId) {
    showToast('该证据没有可定位的章节信息', true);
    return;
  }
  try {
    const detail = await api('/api/documents/' + encodeURIComponent(evidence.documentId)
        + '/sections/' + encodeURIComponent(evidence.sectionId));
    $('#section-title').textContent = detail.documentTitle;
    const meta = $('#section-meta');
    meta.replaceChildren();
    [detail.projectName, detail.headingPath, 'v' + detail.version, detail.effectiveDate, detail.status,
      '来源 ' + detail.sourceUri].forEach(value => {
      const span = document.createElement('span');
      span.textContent = value;
      meta.append(span);
    });
    renderSourceWithHighlight(detail.content, evidence.snippet);
    sectionDialog.showModal();
  } catch (error) {
    showToast(error.message, true);
  }
}


async function loadEvaluationHistory() {
  try {
    const items = await api('/api/evaluations/history?limit=6');
    const container = $('#evaluation-history');
    container.replaceChildren();
    if (!items.length) {
      container.classList.add('empty-state');
      container.textContent = '暂无历史评测。';
      return;
    }
    container.classList.remove('empty-state');
    const header = document.createElement('div');
    header.className = 'evaluation-history-row';
    ['时间', '通过率', '引用覆盖', '证据召回', '拒答准确率', '权限违规'].forEach(text => {
      const span = document.createElement('span');
      span.textContent = text;
      header.append(span);
    });
    container.append(header);
    items.forEach(item => {
      const row = document.createElement('div');
      row.className = 'evaluation-history-row';
      [formatTime(item.createdAt), Math.round(item.passRate * 100) + '%',
        Math.round(item.citationCoverage * 100) + '%', Math.round(item.evidenceRecall * 100) + '%',
        Math.round(item.abstentionAccuracy * 100) + '%', String(item.permissionViolations)].forEach(text => {
        const span = document.createElement('span');
        span.textContent = text;
        row.append(span);
      });
      container.append(row);
    });
  } catch (error) {
    showToast(error.message, true);
  }
}


async function loadDocuments() {
  const projectIds = state.selected.size ? Array.from(state.selected) : state.projects.map(item => item.id);
  if (!projectIds.length) {
    state.documents = [];
    renderDocuments();
    return;
  }
  try {
    const groups = await Promise.all(projectIds.map(projectId =>
      api('/api/projects/' + encodeURIComponent(projectId) + '/documents')));
    state.documents = groups.flat();
    renderDocuments();
  } catch (error) {
    showToast(error.message, true);
  }
}

function renderDocuments() {
  documentList.replaceChildren();
  if (!state.documents.length) {
    documentList.classList.add('empty-state');
    documentList.textContent = '暂无文档。';
    return;
  }
  documentList.classList.remove('empty-state');
  const projectNames = new Map(state.projects.map(item => [item.id, item.name]));
  state.documents
      .slice()
      .sort((a, b) => (a.status === b.status ? b.effectiveDate.localeCompare(a.effectiveDate) : a.status === 'ACTIVE' ? -1 : 1))
      .forEach(doc => {
        const card = document.createElement('article');
        card.className = 'memory-card';
        const header = document.createElement('header');
        const title = document.createElement('h4');
        title.textContent = doc.title;
        const badge = document.createElement('span');
        badge.className = 'badge' + (doc.status === 'ACTIVE' ? '' : ' danger');
        badge.textContent = doc.status;
        header.append(title, badge);
        const meta = document.createElement('div');
        meta.className = 'evidence-meta';
        [projectNames.get(doc.projectId) || doc.projectId, 'v' + doc.version, doc.effectiveDate,
          doc.sectionCount + ' 个章节', doc.lineageKey || '默认谱系'].forEach(text => {
          const span = document.createElement('span');
          span.textContent = text;
          meta.append(span);
        });
        const body = document.createElement('p');
        body.textContent = doc.l0Summary;
        const actions = document.createElement('div');
        actions.className = 'memory-actions';
        const historyButton = document.createElement('button');
        historyButton.type = 'button';
        historyButton.className = 'small-button';
        historyButton.textContent = '版本链';
        historyButton.addEventListener('click', () => openDocumentHistory(doc));
        actions.append(historyButton);
        const canEdit = canEditProject(doc.projectId);
        if (doc.status === 'ACTIVE' && canEdit) {
          const archiveButton = document.createElement('button');
          archiveButton.type = 'button';
          archiveButton.className = 'small-button danger';
          archiveButton.textContent = '归档';
          archiveButton.addEventListener('click', () => archiveDocument(doc));
          actions.append(archiveButton);
        } else if (doc.status === 'ARCHIVED' && canEdit) {
          const restoreButton = document.createElement('button');
          restoreButton.type = 'button';
          restoreButton.className = 'small-button';
          restoreButton.textContent = '恢复';
          restoreButton.addEventListener('click', () => restoreDocument(doc));
          actions.append(restoreButton);
        }
        card.append(header, meta, body, actions);
        documentList.append(card);
      });
}


async function loadHealthOverview() {
  try {
    const projectId = state.selected.size === 1 ? Array.from(state.selected)[0] : null;
    const health = await api('/api/health/overview' + (projectId ? '?projectId=' + encodeURIComponent(projectId) : ''));
    const items = $('#health-overview').children;
    items[0].querySelector('strong').textContent = health.activeDocuments;
    items[1].querySelector('strong').textContent = health.supersededDocuments;
    items[2].querySelector('strong').textContent = health.archivedDocuments;
    items[3].querySelector('strong').textContent = health.proposedMemories;
    items[4].querySelector('strong').textContent = health.openGaps;
    items[5].querySelector('strong').textContent = health.lastEvaluationPassRate == null
      ? '--' : Math.round(health.lastEvaluationPassRate * 100) + '%';
    items[6].querySelector('strong').textContent = Math.round(health.helpfulRate * 100) + '%';
    items[7].querySelector('strong').textContent = health.feedbackTotal;
    const warnings = health.warnings || [];
    const warningBox = $('#health-warnings');
    warningBox.replaceChildren();
    warningBox.classList.toggle('hidden', warnings.length === 0);
    warnings.forEach(text => {
      const item = document.createElement('div');
      item.textContent = text;
      warningBox.append(item);
    });
  } catch (error) {
    showToast(error.message, true);
  }
}


async function loadRunHistory() {
  try {
    const items = await api('/api/runs?limit=8');
    const metrics = await api('/api/runs/metrics');
    runHistory.replaceChildren();
    const summary = document.createElement('div');
    summary.className = 'run-metrics';
    [['运行', metrics.total], ['P95', metrics.p95DurationMs + 'ms'],
      ['引用覆盖', Math.round(metrics.averageCitationCoverage * 100) + '%']].forEach(([label, value]) => {
      const cell = document.createElement('div');
      const strong = document.createElement('strong');
      strong.textContent = value;
      const span = document.createElement('span');
      span.textContent = label;
      cell.append(strong, span);
      summary.append(cell);
    });
    runHistory.append(summary);
    const title = document.createElement('h4');
    title.textContent = 'RECENT RUNS';
    runHistory.append(title);
    if (!items.length) {
      const empty = document.createElement('div');
      empty.className = 'run-history-row';
      empty.textContent = '暂无运行记录';
      runHistory.append(empty);
      return;
    }
    items.forEach(item => {
      const row = document.createElement('div');
      row.className = 'run-history-row';
      const question = document.createElement('strong');
      question.textContent = item.question;
      const meta = document.createElement('span');
      meta.textContent = item.status + ' · ' + item.durationMs + 'ms · ' + formatTime(item.createdAt);
      row.append(question, meta);
      row.classList.add('clickable');
      row.addEventListener('click', () => loadRunDetail(item.runId));
      runHistory.append(row);
    });
  } catch (error) {
    showToast(error.message, true);
  }
}


async function submitFeedback(runId, rating, presetComment, button) {
  let comment = presetComment;
  if (rating === 'NOT_HELPFUL') {
    comment = window.prompt('请描述需要改进的地方（可选）', '') || '';
  }
  try {
    await api('/api/runs/' + encodeURIComponent(runId) + '/feedback', {
      method: 'POST',
      body: JSON.stringify({ rating, comment })
    });
    button.textContent = rating === 'HELPFUL' ? '已标记有帮助' : '反馈已提交';
    button.disabled = true;
    showToast('反馈已记录');
  } catch (error) {
    showToast(error.message, true);
  }
}


function renderSourceWithHighlight(content, snippet) {
  const target = $('#section-content');
  target.replaceChildren();
  const search = (snippet || '').replace(/\.\.\.$/, '').slice(0, 100);
  const index = search ? content.indexOf(search) : -1;
  if (index < 0) {
    target.textContent = content;
    return;
  }
  target.append(document.createTextNode(content.slice(0, index)));
  const mark = document.createElement('mark');
  mark.textContent = content.slice(index, index + search.length);
  target.append(mark, document.createTextNode(content.slice(index + search.length)));
}


async function openDocumentHistory(documentItem) {
  try {
    const history = await api('/api/documents/' + encodeURIComponent(documentItem.id) + '/history');
    $('#section-title').textContent = documentItem.title + ' · 版本链';
    const meta = $('#section-meta');
    meta.replaceChildren();
    const projectName = state.projects.find(item => item.id === documentItem.projectId)?.name || documentItem.projectId;
    [projectName, '共 ' + history.length + ' 个版本'].forEach(value => {
      const span = document.createElement('span');
      span.textContent = value;
      meta.append(span);
    });
    for (let index = 0; index + 1 < history.length; index++) {
      const newer = history[index];
      const older = history[index + 1];
      const compare = document.createElement('button');
      compare.type = 'button';
      compare.className = 'small-button';
      compare.textContent = '比较 v' + older.version + ' → v' + newer.version;
      compare.addEventListener('click', () => openDocumentDiff(older, newer));
      meta.append(compare);
    }
    $('#section-content').textContent = history.map((item, index) =>
      'v' + item.version + '  ' + item.status + '  ' + item.effectiveDate + '\n'
      + item.title + '\n' + item.l0Summary
      + (item.supersededBy ? '\n被 ' + item.supersededBy + ' 取代' : '')
      + (index < history.length - 1 ? '\n\n' : '')
    ).join('');
    sectionDialog.showModal();
  } catch (error) {
    showToast(error.message, true);
  }
}


async function createProject(event) {
  event.preventDefault();
  const members = $('#project-members').value.split(',').map(item => item.trim()).filter(Boolean);
  try {
    const project = state.projectArchive
      ? await api('/api/projects/archive/import', {
          method: 'POST',
          body: JSON.stringify(state.projectArchive)
        })
      : await api('/api/projects', {
          method: 'POST',
          body: JSON.stringify({
            name: $('#project-name').value.trim(),
            description: $('#project-description').value.trim(),
            memberIds: members
          })
        });
    projectDialog.close();
    $('#project-form').reset();
    state.projectArchive = null;
    showToast('项目已创建');
    await loadProjects();
    state.selected = new Set([project.id]);
    renderProjects();
  } catch (error) {
    showToast(error.message, true);
  }
}


async function manageProjectMembers(project) {
  const current = (project.memberIds || []).join(',');
  const available = (state.users || []).map(user => user.id).join(', ');
  const value = window.prompt('输入成员 ID，以逗号分隔\n可选：' + available, current);
  if (value == null) return;
  const memberIds = value.split(',').map(item => item.trim()).filter(Boolean);
  try {
    await api('/api/projects/' + encodeURIComponent(project.id) + '/members', {
      method: 'POST',
      body: JSON.stringify({ memberIds })
    });
    showToast('项目成员已更新');
    await loadProjects();
  } catch (error) {
    showToast(error.message, true);
  }
}


async function loadRunDetail(runId) {
  try {
    const run = await api('/api/runs/' + encodeURIComponent(runId));
    runBadge.textContent = run.runId;
    const evidence = (run.evidence || []).map(item => ({
      ...item,
      score: item.finalScore
    }));
    renderEvidence(evidence);
    renderTrace(run.trace || []);
    switchTab('trace');
    showToast('已加载运行 ' + run.runId);
  } catch (error) {
    showToast(error.message, true);
  }
}


async function previewRetrieval() {
  const question = questionInput.value.trim();
  if (!question) {
    showToast('请先输入要检索的问题', true);
    return;
  }
  const button = $('#preview-button');
  button.disabled = true;
  try {
    const preview = await api('/api/retrieval/preview', {
      method: 'POST',
      body: JSON.stringify({ question, projectIds: Array.from(state.selected) })
    });
    renderEvidence(preview.evidence || []);
    renderTrace([{
      stage: 'preview',
      status: 'ok',
      detail: 'intent=' + preview.intent + '；queries=' + (preview.queries || []).join(' | ')
        + '；memoryHints=' + (preview.memoryHints || []).length,
      durationMs: 0
    }]);
    runBadge.textContent = 'retrieval-preview';
    appendPreviewMessage(preview);
    switchTab('evidence');
    showToast('检索预览完成，未生成回答');
  } catch (error) {
    showToast(error.message, true);
  } finally {
    button.disabled = false;
  }
}

function appendPreviewMessage(preview) {
  const article = document.createElement('article');
  article.className = 'message message-agent';
  const label = document.createElement('div');
  label.className = 'message-label';
  label.textContent = 'Retrieval preview';
  const text = document.createElement('div');
  text.className = 'answer-text';
  text.textContent = '检索意图：' + preview.intent + '\n查询计划：' + (preview.queries || []).join(' | ')
    + '\n命中证据：' + (preview.evidence || []).length + ' 条\n此操作未调用回答生成模型。';
  article.append(label, text);
  chatLog.append(article);
  article.scrollIntoView({ behavior: 'smooth', block: 'end' });
}


function loadIngestFile(event) {
  const file = event.target.files?.[0];
  state.ingestFile = file || null;
  if (!file) {
    $('#ingest-content').required = true;
    return;
  }
  if (!$('#ingest-title').value.trim()) {
    $('#ingest-title').value = file.name.replace(/\.[^.]+$/, '');
  }
  if (/\.(pdf|docx)$/i.test(file.name)) {
    $('#ingest-content').value = '';
    $('#ingest-content').required = false;
    showToast('文件将在服务器端解析');
    return;
  }
  $('#ingest-content').required = true;
  const reader = new FileReader();
  reader.onload = () => {
    $('#ingest-content').value = String(reader.result || '');
    if (!$('#ingest-title').value.trim()) {
      $('#ingest-title').value = file.name.replace(/\.(md|markdown|txt)$/i, '');
    }
    showToast('文件内容已读取到编辑器');
  };
  reader.onerror = () => showToast('读取文件失败', true);
  reader.readAsText(file, 'UTF-8');
}


function loadProjectArchiveFile(event) {
  const file = event.target.files?.[0];
  if (!file) {
    state.projectArchive = null;
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    try {
      state.projectArchive = JSON.parse(String(reader.result || '{}'));
      if (state.projectArchive.project?.name && !$('#project-name').value.trim()) {
        $('#project-name').value = state.projectArchive.project.name + '（导入）';
      }
      showToast('项目归档已读取');
    } catch (error) {
      state.projectArchive = null;
      showToast('项目归档 JSON 无法解析', true);
    }
  };
  reader.readAsText(file, 'UTF-8');
}

async function exportProject(project) {
  try {
    const archive = await api('/api/projects/' + encodeURIComponent(project.id) + '/archive');
    const blob = new Blob([JSON.stringify(archive, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = (project.name || project.id).replace(/[\\/:*?"<>|]/g, '_') + '-archive.json';
    link.click();
    URL.revokeObjectURL(url);
    showToast('项目归档已导出');
  } catch (error) {
    showToast(error.message, true);
  }
}


function prefillGap(gap) {
  const projectId = gap.projectIds?.[0];
  if (!projectId) {
    showToast('知识缺口没有可用的项目范围', true);
    return;
  }
  ingestProject.value = projectId;
  $('#ingest-title').value = '知识缺口补录：' + truncateText(gap.question, 48);
  $('#ingest-content').value = '# 背景\n' + gap.question + '\n\n# 结论\n';
  $('#ingest-content').required = true;
  state.ingestFile = null;
  $('#ingest-file').value = '';
  $('#ingest-title').scrollIntoView({ behavior: 'smooth', block: 'center' });
  $('#ingest-title').focus();
  showToast('已预填关联项目，请补充知识结论');
}


async function archiveDocument(documentItem) {
  if (!window.confirm('归档后文档将不再参与检索，是否继续？')) return;
  try {
    await api('/api/documents/' + encodeURIComponent(documentItem.id) + '/archive', {
      method: 'POST',
      body: '{}'
    });
    showToast('文档已归档');
    await loadDocuments();
    await loadHealthOverview();
  } catch (error) {
    showToast(error.message, true);
  }
}


function renderAnswerContent(container, markdown) {
  container.replaceChildren();
  const lines = String(markdown || '').split(/\r?\n/);
  let list = null;
  const flushList = () => {
    list = null;
  };
  lines.forEach(raw => {
    const line = raw.trim();
    if (!line) {
      flushList();
      const gap = document.createElement('div');
      gap.className = 'answer-gap';
      container.append(gap);
      return;
    }
    if (line.startsWith('- ')) {
      if (!list) {
        list = document.createElement('ul');
        list.className = 'answer-list';
        container.append(list);
      }
      const item = document.createElement('li');
      appendInline(item, line.slice(2));
      list.append(item);
      return;
    }
    flushList();
    if (line.startsWith('**') && line.endsWith('**') && line.length > 4) {
      const heading = document.createElement('h4');
      appendInline(heading, line);
      container.append(heading);
      return;
    }
    const paragraph = document.createElement('p');
    appendInline(paragraph, line);
    container.append(paragraph);
  });
}

function appendInline(container, text) {
  const pattern = /(\*\*[^*]+\*\*|\[E\d+\])/g;
  let cursor = 0;
  let match;
  while ((match = pattern.exec(text)) !== null) {
    if (match.index > cursor) container.append(document.createTextNode(text.slice(cursor, match.index)));
    const token = match[0];
    if (token.startsWith('**')) {
      const strong = document.createElement('strong');
      strong.textContent = token.slice(2, -2);
      container.append(strong);
    } else {
      const citation = document.createElement('span');
      citation.className = 'inline-citation';
      citation.textContent = token;
      container.append(citation);
    }
    cursor = match.index + token.length;
  }
  if (cursor < text.length) container.append(document.createTextNode(text.slice(cursor)));
}


function resetSession() {
  state.sessionId = null;
  state.lastQuestion = '';
  localStorage.removeItem('project-atlas-session');
  resetConversation();
  loadProjects();
  showToast('已开始新会话');
}


async function openDocumentDiff(older, newer) {
  try {
    const diff = await api('/api/documents/' + encodeURIComponent(older.id)
        + '/diff/' + encodeURIComponent(newer.id));
    $('#section-title').textContent = '版本差异 · v' + dictVersion(older) + ' → v' + dictVersion(newer);
    const meta = $('#section-meta');
    meta.replaceChildren();
    const added = document.createElement('span');
    added.textContent = '新增 ' + diff.added.length + ' 行';
    const removed = document.createElement('span');
    removed.textContent = '删除 ' + diff.removed.length + ' 行';
    meta.append(added, removed);
    const lines = [];
    diff.added.forEach(line => lines.push('+ ' + line));
    diff.removed.forEach(line => lines.push('- ' + line));
    $('#section-content').textContent = lines.length ? lines.join('\n') : '两个版本没有可见差异。';
    sectionDialog.showModal();
  } catch (error) {
    showToast(error.message, true);
  }
}

function dictVersion(item) {
  return item?.version ?? '?';
}


async function manageProjectRole(project) {
  const available = (state.users || []).filter(user => (project.memberIds || []).includes(user.id) && user.id !== project.ownerId).map(user => user.id).join(', ');
  const memberId = window.prompt('输入要设置角色的成员 ID\n可选：' + available, (project.memberIds || []).find(id => id !== project.ownerId) || '');
  if (!memberId) return;
  const role = (window.prompt('输入角色：EDITOR 或 VIEWER', 'EDITOR') || '').trim().toUpperCase();
  if (!role) return;
  try {
    await api('/api/projects/' + encodeURIComponent(project.id) + '/roles', {
      method: 'POST',
      body: JSON.stringify({ memberId, role })
    });
    showToast(memberId + ' 的角色已更新为 ' + role);
  } catch (error) {
    showToast(error.message, true);
  }
}


async function restoreDocument(documentItem) {
  try {
    const restored = await api('/api/documents/' + encodeURIComponent(documentItem.id) + '/restore', {
      method: 'POST',
      body: '{}'
    });
    showToast('文档状态已恢复为 ' + restored.status);
    await loadDocuments();
    await loadHealthOverview();
  } catch (error) {
    showToast(error.message, true);
  }
}


async function loadRuntimeStatus() {
  try {
    const runtime = await api('/api/runtime');
    const embedding = runtime.realEmbeddingEnabled ? 'real embedding' : 'hash embedding';
    $('#runtime-status').textContent = runtime.provider + ' / ' + runtime.model + ' / ' + embedding;
  } catch (error) {
    $('#runtime-status').textContent = 'runtime unavailable';
  }
}


function roleForProject(projectId) {
  const project = state.projects.find(item => item.id === projectId);
  if (!project) return 'VIEWER';
  return project.memberRoles?.[state.user]
    || (project.ownerId === state.user ? 'OWNER' : 'VIEWER');
}

function canEditProject(projectId) {
  return ['OWNER', 'EDITOR'].includes(roleForProject(projectId));
}

function updateIngestPermission() {
  const projectId = ingestProject.value;
  const allowed = projectId ? canEditProject(projectId) : false;
  document.querySelectorAll('#ingest-form input, #ingest-form textarea, #ingest-form button')
      .forEach(control => { control.disabled = !allowed; });
  const submit = document.querySelector('#ingest-form button[type="submit"]');
  if (submit) submit.title = allowed ? '' : '当前角色为 VIEWER，不能修改知识';
}


async function loadUserDirectory() {
  try {
    const users = await api('/api/users');
    state.users = users;
    const datalist = $('#user-directory');
    datalist.replaceChildren();
    users.forEach(user => {
      const option = document.createElement('option');
      option.value = user.id;
      option.label = user.displayName;
      datalist.append(option);
    });
  } catch (error) {
    state.users = [];
  }
}


async function downloadGapReport() {
  const projectId = state.selected.size === 1 ? Array.from(state.selected)[0] : null;
  try {
    const response = await fetch('/api/gaps/export' + (projectId ? '?projectId=' + encodeURIComponent(projectId) : ''), {
      headers: { 'X-User-Id': state.user }
    });
    if (!response.ok) {
      const errorBody = await response.json().catch(() => ({}));
      throw new Error(errorBody.message || '导出知识缺口失败');
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'knowledge-gaps.md';
    link.click();
    URL.revokeObjectURL(url);
    showToast('知识缺口清单已导出');
  } catch (error) {
    showToast(error.message, true);
  }
}

init();
