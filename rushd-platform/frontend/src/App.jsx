import { useEffect } from 'react'
import { Routes, Route, useLocation, useNavigate } from 'react-router-dom'
import { onAuthenticationLost } from './services/api'
import Navbar from './components/Navbar'
import Footer from './components/Footer'
import Home from './pages/Home'
import Login from './pages/Login'
import Properties from './pages/Properties'
import Dashboard from './pages/Dashboard'
import PropertyDetails from './pages/PropertyDetails'
import './pages/Pages.css'
import './pages/Management.css'

function App() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  useEffect(
    () =>
      onAuthenticationLost(() => {
        if (window.location.pathname === '/dashboard') navigate('/login', { replace: true })
      }),
    [navigate],
  )
  useEffect(() => {
    window.scrollTo(0, 0)
  }, [pathname])

  return (
    <>
      <a href="#main-content" className="skip-link">
        انتقل إلى المحتوى
      </a>
      <Navbar />
      <main id="main-content" className="main-content" tabIndex={-1}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/properties" element={<Properties />} />
          <Route path="/properties/:id" element={<PropertyDetails />} />
          <Route path="/dashboard" element={<Dashboard />} />
        </Routes>
      </main>
      <Footer />
    </>
  )
}

export default App
