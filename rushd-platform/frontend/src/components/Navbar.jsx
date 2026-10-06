import { useState } from 'react'
import { NavLink, Link } from 'react-router-dom'
import { useAuth } from '../services/authState'
import Brand from './Brand'
import Icon from './Icon'
import './Navbar.css'

function Navbar() {
  const { user, logout } = useAuth()
  const [isOpen, setIsOpen] = useState(false)
  const [logoutError, setLogoutError] = useState('')
  const [loggingOut, setLoggingOut] = useState(false)
  const closeMenu = () => setIsOpen(false)
  return (
    <header className="site-header">
      <nav className="navbar container" aria-label="التنقل الرئيسي">
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
            <li>
              <NavLink to="/dashboard" onClick={closeMenu}>
                {user?.role === 'ADMIN' ? 'إدارة العقارات' : 'حسابي'}
              </NavLink>
            </li>
          </ul>
          {user ? (
            <button
              type="button"
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
