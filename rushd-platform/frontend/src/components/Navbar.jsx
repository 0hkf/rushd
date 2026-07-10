import { NavLink } from 'react-router-dom'
import './Navbar.css'

function Navbar() {
  return (
    <nav className="navbar" dir="rtl">
      <div className="navbar-brand">
        <NavLink to="/" className="brand-link">رُشد</NavLink>
      </div>
      <ul className="navbar-links">
        <li>
          <NavLink to="/" end className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            الرئيسية
          </NavLink>
        </li>
        <li>
          <NavLink to="/properties" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            العقارات
          </NavLink>
        </li>
        <li>
          <NavLink to="/dashboard" className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}>
            لوحة التحكم
          </NavLink>
        </li>
        <li>
          <NavLink to="/login" className={({ isActive }) => isActive ? 'nav-link active login-link' : 'nav-link login-link'}>
            تسجيل الدخول
          </NavLink>
        </li>
      </ul>
    </nav>
  )
}

export default Navbar
