import { cloneElement, useEffect, useId, useRef, useState } from 'react';
import { api, authenticate, restoreSession, logout, errorMessage } from './api';

const emptyTask = { title: '', description: '', priority: 'MEDIUM', dueDate: '', completed: false };
function Brand() { return <div className="brand"><span className="brand-mark">g.</span><span>gather<span className="brand-dot">.</span></span></div>; }
function Field({ label, children }) { const id = useId(); return <div className="field"><label htmlFor={id}>{label}</label>{cloneElement(children, { id })}</div>; }
function ErrorBox({ message }) { return message ? <div className="error" role="alert">{message}</div> : null; }
function Auth({ onLogin }) {
  const [mode, setMode] = useState('login'), [error, setError] = useState(''), [busy, setBusy] = useState(false);
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('');
    const values = Object.fromEntries(new FormData(event.currentTarget));
    try { onLogin(await authenticate(mode, values)); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  return <main className="auth-shell">
    <section className="auth-story"><Brand/><div><p className="eyebrow">A LITTLE SPACE FOR WHAT MATTERS</p>
      <h1>Less on your mind.<br/><em>More in your day.</em></h1><p>Bring your projects, plans, and next steps together. One small task at a time.</p>
      <div className="sample-card"><span className="sample-check">✓</span><div>Make room for a fresh start<small>Your next chapter starts here</small></div><span className="pill">Today</span></div>
    </div><small>A personal workspace. A clearer head.</small></section>
    <section className="auth-form"><p className="eyebrow">YOUR WORKSPACE AWAITS</p><h2>{mode === 'login' ? 'Welcome back.' : 'Make yourself at home.'}</h2>
      <p className="muted">{mode === 'login' ? 'Sign in and pick up where you left off.' : 'Create an account to start gathering your ideas.'}</p>
      <form onSubmit={submit}><ErrorBox message={error}/>
        {mode === 'register' && <Field label="Display name"><input name="displayName" autoComplete="name" maxLength={80} required/></Field>}
        <Field label="Email"><input name="email" type="email" autoComplete="email" maxLength={254} required/></Field>
        <Field label="Password"><input name="password" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} minLength={mode === 'register' ? 10 : undefined} maxLength={72} required/></Field>
        {mode === 'register' && <small className="muted">Use at least 10 characters (maximum 72 UTF-8 bytes).</small>}
        <button className="primary wide" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in →' : 'Create account →'}</button>
      </form><p className="auth-switch">{mode === 'login' ? 'New here?' : 'Already have an account?'} <button className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>{mode === 'login' ? 'Create an account' : 'Sign in'}</button></p>
    </section></main>;
}
function TaskEditor({ task, onSave, onCancel, busy, error }) {
  const [draft, setDraft] = useState(task || emptyTask);
  const dialog = useRef(null);
  useEffect(() => { dialog.current.showModal(); }, []);
  const change = (key, value) => setDraft({ ...draft, [key]: value });
  return <dialog ref={dialog} aria-labelledby="task-heading" className="modal" onCancel={event => { event.preventDefault(); if (!busy) onCancel(); }}>
    <div className="section-heading"><h2 id="task-heading">{task ? 'Edit task' : 'A new next step'}</h2><button className="icon-button" aria-label="Close task editor" onClick={onCancel} disabled={busy}>×</button></div>
    <ErrorBox message={error}/>
    <form onSubmit={event => { event.preventDefault(); onSave({ title: draft.title, description: draft.description, priority: draft.priority, dueDate: draft.dueDate || null, completed: draft.completed }); }}>
      <Field label="Task title"><input autoFocus value={draft.title} onChange={e => change('title', e.target.value)} required maxLength={200}/></Field>
      <Field label="Description"><textarea rows={3} value={draft.description} onChange={e => change('description', e.target.value)} maxLength={4000}/></Field>
      <div className="form-row"><Field label="Priority"><select value={draft.priority} onChange={e => change('priority', e.target.value)}><option>LOW</option><option>MEDIUM</option><option>HIGH</option></select></Field>
      <Field label="Due date"><input type="date" value={draft.dueDate || ''} onChange={e => change('dueDate', e.target.value)}/></Field></div>
      <label className="check-label"><input type="checkbox" checked={draft.completed} onChange={e => change('completed', e.target.checked)}/>Completed</label>
      <div className="modal-actions"><button type="button" className="secondary" onClick={onCancel} disabled={busy}>Cancel</button><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save task'}</button></div>
    </form></dialog>;
}
function Profile({ user, onUpdate, run, busy }) {
  return <section className="profile-card"><p className="eyebrow">ACCOUNT</p><h1>Your profile</h1><p className="muted">A little more you.</p>
    <form onSubmit={event => { event.preventDefault(); const displayName = new FormData(event.currentTarget).get('displayName'); run(async () => onUpdate(await api('/me', { method: 'PUT', body: { displayName } }))); }}>
      <Field label="Display name"><input name="displayName" defaultValue={user.displayName} maxLength={80} required/></Field>
      <Field label="Email"><input value={user.email} disabled/></Field><button className="primary" disabled={busy}>Save profile</button>
    </form></section>;
}
export default function App() {
  const [user, setUser] = useState(null), [initializing, setInitializing] = useState(true);
  const [projects, setProjects] = useState([]), [route, setRoute] = useState(location.hash || '#/all');
  const [page, setPage] = useState(0), [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0 });
  const [query, setQuery] = useState(''), [search, setSearch] = useState(''), [status, setStatus] = useState(''), [priority, setPriority] = useState('');
  const [sort, setSort] = useState('createdAt'), [editor, setEditor] = useState(null), [revision, setRevision] = useState(0);
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [loading, setLoading] = useState(false);
  const [projectName, setProjectName] = useState('');
  const projectId = route.startsWith('#/projects/') ? Number(route.split('/')[2]) : null;
  const selected = projects.find(p => p.id === projectId);
  const profile = route === '#/profile';
  useEffect(() => {
    restoreSession().then(setUser).catch(e => { if (e.status !== 401) setError(errorMessage(e)); }).finally(() => setInitializing(false));
    const expired = () => { setUser(null); setProjects([]); setEditor(null); setError('Your session has expired. Please sign in again.'); };
    const navigate = () => { setRoute(location.hash || '#/all'); setPage(0); setEditor(null); setError(''); };
    addEventListener('session-expired', expired); addEventListener('hashchange', navigate);
    return () => { removeEventListener('session-expired', expired); removeEventListener('hashchange', navigate); };
  }, []);
  useEffect(() => { const timer = setTimeout(() => { setSearch(query); setPage(0); }, 250); return () => clearTimeout(timer); }, [query]);
  useEffect(() => {
    if (!user) return;
    let active = true;
    api('/projects').then(result => { if (active) setProjects(result); }).catch(e => { if (active) setError(errorMessage(e)); });
    return () => { active = false; };
  }, [user, revision]);
  useEffect(() => {
    if (!user || profile) return;
    let active = true; setLoading(true);
    const params = new URLSearchParams({ q: search, page, size: 10, sort, direction: sort === 'title' || sort === 'dueDate' ? 'asc' : 'desc' });
    if (status) params.set('completed', status);
    if (priority) params.set('priority', priority);
    api((projectId ? '/projects/' + projectId + '/tasks' : '/tasks') + '?' + params).then(result => {
      if (!active) return;
      if (result.content.length === 0 && page > 0) { setPage(page - 1); return; }
      setData(result);
    }).catch(e => { if (active) { setError(errorMessage(e)); setData({ content: [], totalElements: 0, totalPages: 0 }); } }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user, profile, projectId, page, search, status, priority, sort, revision]);
  async function run(action) {
    if (busy) return; setBusy(true); setError('');
    try { await action(); setRevision(v => v + 1); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  async function saveTask(body) {
    await run(async () => { await api(editor.id ? '/tasks/' + editor.id : '/projects/' + projectId + '/tasks', { method: editor.id ? 'PUT' : 'POST', body }); setEditor(null); });
  }
  async function signOut() { await run(async () => { await logout(); setUser(null); setProjects([]); setEditor(null); location.hash = '/all'; }); }
  if (initializing) return <main className="boot"><Brand/><p>Opening your workspace…</p></main>;
  if (!user) return <><ErrorBox message={error}/><Auth onLogin={value => { setUser(value); setError(''); }}/></>;
  return <div className="workspace"><aside className="sidebar"><Brand/><p className="workspace-label">PERSONAL WORKSPACE</p>
    <nav aria-label="Workspace"><a href="#/all" className={route === '#/all' ? 'active nav-item' : 'nav-item'}><span>▦</span>All tasks</a>
      <div className="nav-heading">YOUR PROJECTS <span>{projects.length}</span></div>
      {projects.map((p, i) => <a key={p.id} href={'#/projects/' + p.id} className={projectId === p.id ? 'active nav-item' : 'nav-item'}><span className={'project-dot dot-' + i % 3}></span>{p.name}</a>)}
    </nav>
    <form className="new-project" onSubmit={event => { event.preventDefault(); run(async () => { const p = await api('/projects', { method: 'POST', body: { name: projectName } }); setProjectName(''); location.hash = '/projects/' + p.id; }); }}>
      <label className="sr-only" htmlFor="project-name">New project name</label><input id="project-name" placeholder="New project name" value={projectName} onChange={e => setProjectName(e.target.value)} maxLength={100} required/><button aria-label="Create project" disabled={busy}>+</button>
    </form><div className="sidebar-note">Small steps.<br/><strong>Meaningful progress.</strong></div>
    <a className="profile-link" href="#/profile"><span className="avatar">{user.displayName[0]?.toUpperCase()}</span><span>{user.displayName}<small>Personal account</small></span><span>↗</span></a>
    </aside>
    <main className="main"><header className="topbar"><span className="breadcrumb">Workspace <span className="crumb">/</span> {profile ? 'Profile' : selected?.name || 'All tasks'}</span><div className="topbar-actions"><span className="today">{new Date().toLocaleDateString('en-US', { month: 'short', day: 'numeric', weekday: 'short' })}</span><button className="secondary" disabled={busy} onClick={signOut}>Sign out</button></div></header>
      <div className="content"><ErrorBox message={error}/>
      {profile ? <Profile key={user.id} user={user} onUpdate={setUser} run={run} busy={busy}/> : <>
        <div className="page-heading"><div><p className="eyebrow">MAKE SPACE FOR PROGRESS</p><h1>{selected?.name || 'All your next steps'}</h1><p className="muted">A clear view of what matters. Take it one task at a time.</p></div>
        <button className="primary" disabled={busy || !selected} title={selected ? 'Create a task' : 'Select or create a project first'} onClick={() => setEditor({})}>＋ New task</button></div>
        {selected && <div className="project-actions"><button className="text-button" disabled={busy} onClick={() => { const name = prompt('Project name', selected.name); if (name?.trim()) run(() => api('/projects/' + selected.id, { method: 'PUT', body: { name } })); }}>Rename project</button><button className="text-button danger" disabled={busy} onClick={() => { if (confirm('Delete this project? It must be empty.')) run(async () => { await api('/projects/' + selected.id, { method: 'DELETE' }); location.hash = '/all'; }); }}>Delete project</button></div>}
        <section className="task-panel"><div className="panel-heading"><h2>Your tasks <span className="count">{data.totalElements}</span></h2><span className="muted small">Your pace. Your priorities. Deployed with GitHub Actions.</span></div>
          <div className="filters"><label className="search"><span>⌕</span><input aria-label="Search tasks" placeholder="Search tasks…" value={query} onChange={e => setQuery(e.target.value)} maxLength={200}/></label>
          <select aria-label="Filter status" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}><option value="">All statuses</option><option value="false">To do</option><option value="true">Completed</option></select>
          <select aria-label="Filter priority" value={priority} onChange={e => { setPriority(e.target.value); setPage(0); }}><option value="">All priorities</option><option>HIGH</option><option>MEDIUM</option><option>LOW</option></select>
          <select aria-label="Sort tasks" value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}><option value="createdAt">Newest first</option><option value="dueDate">Due date</option><option value="title">Title A–Z</option><option value="updatedAt">Recently updated</option></select></div>
          <div aria-live="polite" className="task-list">{loading ? <div className="empty">Loading your tasks…</div> : data.content.length ? data.content.map(task => <article className={'task-row' + (task.completed ? ' completed' : '')} key={task.id}>
            <input aria-label={'Complete ' + task.title} type="checkbox" checked={task.completed} disabled={busy} onChange={() => run(() => api('/tasks/' + task.id, { method: 'PUT', body: { title: task.title, description: task.description, priority: task.priority, dueDate: task.dueDate, completed: !task.completed } }))}/>
            <div className="task-info"><button className="task-title" onClick={() => setEditor(task)}>{task.title}</button><p>{task.description || projects.find(p => p.id === task.projectId)?.name}</p></div>
            <span className={'priority priority-' + task.priority.toLowerCase()}>{task.priority.toLowerCase()}</span><span className="due">{task.dueDate ? new Date(task.dueDate + 'T12:00:00').toLocaleDateString(undefined, { month: 'short', day: 'numeric' }) : 'No date'}</span>
            <button className="icon-button delete-task" aria-label={'Delete ' + task.title} disabled={busy} onClick={() => { if (confirm('Delete “' + task.title + '”?')) run(() => api('/tasks/' + task.id, { method: 'DELETE' })); }}>×</button>
          </article>) : <div className="empty"><span className="empty-icon">✓</span><h3>{projects.length ? 'A little breathing room.' : 'Your next chapter starts here.'}</h3><p>{projects.length ? selected ? 'Add a task, or adjust your filters to find what you need.' : 'Select a project to add a task.' : 'Create your first project in the sidebar, then add a task.'}</p></div>}</div>
          <footer className="pagination"><span>{data.totalElements} matching tasks</span><div><button className="secondary" disabled={loading || page === 0} onClick={() => setPage(p => p - 1)}>← Previous</button><span>{page + 1} / {Math.max(data.totalPages, 1)}</span><button className="secondary" disabled={loading || page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next →</button></div></footer>
        </section><p className="bottom-note">One thing at a time is still moving forward.</p></>}
      </div>
    </main>{editor && <TaskEditor key={editor.id || 'new'} task={editor.id ? editor : null} onSave={saveTask} onCancel={() => setEditor(null)} busy={busy} error={error}/>}</div>;
}
