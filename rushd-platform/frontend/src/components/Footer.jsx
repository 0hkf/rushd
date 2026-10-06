import { Link } from 'react-router-dom'
import Brand from './Brand'

function Footer() {
  return (
    <footer className="site-footer">
      <div className="container footer-inner">
        <Link to="/" aria-label="روافد العقارية — الرئيسية">
          <Brand />
        </Link>
        <p className="footer-copy">رؤية أوضح. قرار عقاري بثقة.</p>
        <div className="footer-links">
          <Link to="/properties">العقارات</Link>
          <Link to="/login">حسابك</Link>
        </div>
      </div>
    </footer>
  )
}

export default Footer
