import { useEffect, useId, useRef, useState } from 'react';
import { api, errorMessage } from './api';

function UserEditor({ user, onSave, onCancel, busy, error }) {
  const [email, setEmail] = useState(user.email), [displayName, setDisplayName] = useState(user.displayName);
  const [password, setPassword] = useState('');
  const dialog = useRef(null), id = useId();
  useEffect(() => { dialog.current.showModal(); }, []);
  return <dialog ref={dialog} className="modal" aria-labelledby={id + '-heading'}
    onCancel={event => { event.preventDefault(); if (!busy) onCancel(); }}>
    <div className="section-heading"><h2 id={id + '-heading'}>Edit account</h2><button className="icon-button" aria-label="Close account editor" disabled={busy} onClick={onCancel}>×</button></div>
    {error && <div className="error" role="alert">{error}</div>}
    <form onSubmit={event => { event.preventDefault(); onSave({ email, displayName, ...(password ? { password } : {}) }); }}>
      <div className="field"><label htmlFor={id + '-name'}>Display name</label><input id={id + '-name'} autoFocus value={displayName} onChange={e => setDisplayName(e.target.value)} maxLength={80} required/></div>
      <div className="field"><label htmlFor={id + '-email'}>Email</label><input id={id + '-email'} type="email" value={email} onChange={e => setEmail(e.target.value)} maxLength={254} required disabled={user.role === 'ADMIN'}/></div>
      <div className="field"><label htmlFor={id + '-password'}>New password</label><input id={id + '-password'} type="password" autoComplete="new-password" value={password} onChange={e => setPassword(e.target.value)} minLength={10} maxLength={72}/>
        <small className="muted">Leave blank to keep the current password. Resetting it signs the account out.</small></div>
      <div className="modal-actions"><button type="button" className="secondary" disabled={busy} onClick={onCancel}>Cancel</button><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save account'}</button></div>
    </form>
  </dialog>;
}

export default function AdminUsers({ currentUser, onUpdate, onPasswordReset }) {
  const [query, setQuery] = useState(''), [search, setSearch] = useState(''), [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], totalElements: 0, totalPages: 0 });
  const [loading, setLoading] = useState(true), [busy, setBusy] = useState(false), [error, setError] = useState('');
  const [notice, setNotice] = useState(''), [editor, setEditor] = useState(null), [revision, setRevision] = useState(0);
  useEffect(() => { const timer = setTimeout(() => { setSearch(query); setPage(0); }, 250); return () => clearTimeout(timer); }, [query]);
  useEffect(() => {
    let active = true; setLoading(true);
    api('/admin/users?' + new URLSearchParams({ q: search, page, size: 10 })).then(result => {
      if (!active) return;
      if (!result.content.length && page > 0) { setPage(page - 1); return; }
      setData(result);
    }).catch(e => { if (active) { setError(errorMessage(e)); setData({ content: [], totalElements: 0, totalPages: 0 }); } })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [search, page, revision]);
  async function run(action) {
    if (busy) return; setBusy(true); setError(''); setNotice('');
    try { await action(); setRevision(v => v + 1); } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  }
  return <>
    <div className="page-heading"><div><p className="eyebrow">ADMINISTRATION</p><h1>Manage users</h1><p className="muted">Find accounts, update their details, or remove them and their workspace.</p></div></div>
    {!editor && error && <div className="error" role="alert">{error}</div>}
    {notice && <p className="account-notice" role="status">{notice}</p>}
    <section className="task-panel"><div className="panel-heading"><h2>Accounts <span className="count">{data.totalElements}</span></h2></div>
      <div className="filters"><label className="search"><span>⌕</span><input aria-label="Search users" placeholder="Search by name or email…" value={query} onChange={e => setQuery(e.target.value)} maxLength={200}/></label></div>
      <div aria-live="polite">{loading ? <div className="empty">Loading accounts…</div> : data.content.length ? data.content.map(account =>
        <article className="account-row" key={account.id} aria-label={'Account ' + account.email}>
          <div className="account-info"><strong>{account.displayName}</strong><p>{account.email}</p></div>
          <span className="account-role">{account.role === 'ADMIN' ? 'Administrator' : 'User'}</span>
          <div className="account-actions"><button className="secondary" disabled={busy} aria-label={'Edit account ' + account.email} onClick={() => { setError(''); setNotice(''); setEditor(account); }}>Edit</button>
            <button className="secondary danger" disabled={busy || account.role === 'ADMIN'} aria-label={'Delete account ' + account.email}
              onClick={() => { if (confirm('Delete ' + account.email + ' and all their projects and tasks? This cannot be undone.')) run(async () => { await api('/admin/users/' + account.id, { method: 'DELETE' }); setNotice('Account and workspace deleted.'); }); }}>Delete</button></div>
        </article>) : <div className="empty">No accounts match your search.</div>}</div>
      <footer className="pagination"><span>{data.totalElements} matching accounts</span><div><button className="secondary" disabled={loading || page === 0} onClick={() => setPage(p => p - 1)}>← Previous</button><span>{page + 1} / {Math.max(data.totalPages, 1)}</span><button className="secondary" disabled={loading || page + 1 >= data.totalPages} onClick={() => setPage(p => p + 1)}>Next →</button></div></footer>
    </section>
    {editor && <UserEditor key={editor.id} user={editor} busy={busy} error={error} onCancel={() => { setEditor(null); setError(''); }}
      onSave={body => run(async () => {
        const saved = await api('/admin/users/' + editor.id, { method: 'PUT', body });
        if (saved.id === currentUser.id) {
          if (body.password) { await onPasswordReset(); return; }
          onUpdate(saved);
        }
        setEditor(null); setNotice('Account updated.');
      })}/>}
  </>;
}
