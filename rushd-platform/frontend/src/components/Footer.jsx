import { Link } from 'react-router-dom'
import Brand from './Brand'
import { useAuth } from '../services/authState'

function Footer() {
  const { user } = useAuth()
  return (
    <footer className="site-footer">
      <div className="container footer-inner">
        <Link to="/" aria-label="روافد العقارية — الرئيسية">
          <Brand />
        </Link>
        <p className="footer-copy">رؤية أوضح. قرار عقاري بثقة.</p>
        <nav className="footer-links" aria-label="روابط التذييل">
          <Link to="/properties">العقارات</Link>
          <Link to="/property-needs">احتياجاتي العقارية</Link>
          <Link to={user ? '/dashboard' : '/login'}>{user ? 'حسابي' : 'تسجيل الدخول'}</Link>
        </nav>
      </div>
    </footer>
  )
}

export default Footer
