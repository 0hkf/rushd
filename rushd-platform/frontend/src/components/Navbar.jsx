import { useEffect, useRef, useState } from 'react'
import { NavLink, Link, useLocation } from 'react-router-dom'
import { useAuth } from '../services/authState'
import Brand from './Brand'
import Icon from './Icon'
import { useComparison } from '../services/comparisonState'
import { marketEnabled } from '../services/marketReferences'
import './Navbar.css'

function Navbar() {
  const { user, logout } = useAuth()
  const { ids } = useComparison()
  const [isOpen, setIsOpen] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const toggle = useRef(null)
  const { pathname } = useLocation()
  useEffect(() => { setIsOpen(false) }, [pathname])
  const closeMenu = () => setIsOpen(false)
  return (
    <header className="site-header">
      <nav className="navbar container" aria-label="التنقل الرئيسي" onKeyDown={(event) => { if (event.key === 'Escape' && isOpen) { setIsOpen(false); toggle.current?.focus() } }}>
        <Link
          to="/"
          className="brand-link"
          aria-label="روافد العقارية — الرئيسية"
          onClick={closeMenu}
        >
          <Brand />
        </Link>
        <button
          className="menu-toggle"
          ref={toggle}
          type="button"
          onClick={() => setIsOpen(!isOpen)}
          aria-expanded={isOpen}
          aria-controls="main-navigation"
          aria-label={isOpen ? 'إغلاق القائمة' : 'فتح القائمة'}
        >
          <Icon name={isOpen ? 'close' : 'menu'} />
        </button>
        <div id="main-navigation" className={`navbar-navigation ${isOpen ? 'is-open' : ''}`}>
          <ul className="navbar-links">
            <li>
              <NavLink to="/" end onClick={closeMenu}>
                الرئيسية
              </NavLink>
            </li>
            <li>
              <NavLink to="/properties" onClick={closeMenu}>
                العقارات
              </NavLink>
            </li>
            <li>
              <NavLink to="/property-needs" onClick={closeMenu}>
                احتياجاتي العقارية
              </NavLink>
            </li>
            <li><NavLink to="/favorites" onClick={closeMenu}>مفضلتي</NavLink></li>
            <li><NavLink to="/compare" onClick={closeMenu}>المقارنة{ids.length > 0 ? ` (${ids.length})` : ''}</NavLink></li>
            {user && <li>
              <NavLink to="/dashboard" onClick={closeMenu}>
                {user?.role === 'ADMIN' ? 'إدارة العقارات' : 'حسابي'}
              </NavLink>
            </li>}
            {user?.role === 'ADMIN' && marketEnabled && <li><NavLink to="/admin/market-references" onClick={closeMenu}>مراجع السوق</NavLink></li>}
          </ul>
          {user ? (
            <button
              type="button"
              data-cloud-draft-logout="true"
              className="button button-outline nav-login"
              disabled={loggingOut}
              onClick={async () => {
                setLoggingOut(true)
                setLogoutError('')
                try {
                  await logout()
                  closeMenu()
                } catch {
                  setLogoutError('تعذّر الخروج. حاول مرة أخرى.')
                } finally {
                  setLoggingOut(false)
                }
              }}
            >
              {loggingOut ? 'جارٍ الخروج…' : 'تسجيل الخروج'}
            </button>
          ) : (
            <NavLink to="/login" className="button button-primary nav-login" onClick={closeMenu}>
              تسجيل الدخول <Icon name="arrow" />
            </NavLink>
          )}
        </div>
      </nav>
      {logoutError && (
        <p className="container feedback feedback-error" role="alert">
          {logoutError}
        </p>
      )}
    </header>
  )
}

export default Navbar
