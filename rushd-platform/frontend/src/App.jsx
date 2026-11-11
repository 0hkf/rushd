import { lazy, useEffect } from 'react'
import { Routes, Route, useLocation, useNavigate } from 'react-router-dom'
import { onAuthenticationLost } from './services/api'
import { interfaceTitle, revealAnchorTarget } from './services/interface'
import Navbar from './components/Navbar'
import Footer from './components/Footer'
import Home from './pages/Home'
import Login from './pages/Login'
import Register from './pages/Register'
import Properties from './pages/Properties'
import AsyncPage from './components/AsyncPage'
import PropertyDetails from './pages/PropertyDetails'
import PropertyNeeds from './pages/PropertyNeeds'
import Favorites from './pages/Favorites'
import PropertyComparison from './pages/PropertyComparison'
import NotFound from './pages/NotFound'
import ComparisonSelection from './components/ComparisonSelection'
import { ComparisonProvider } from './services/ComparisonContext'
import { FavoritesProvider } from './services/FavoritesContext'
import './pages/Pages.css'
import './pages/Management.css'
import './components/Interface.css'

const Dashboard = lazy(() => import('./pages/Dashboard'))
const MarketReferences = lazy(() => import('./pages/MarketReferences'))

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route path="/properties" element={<Properties />} />
      <Route path="/properties/:id" element={<PropertyDetails />} />
      <Route path="/dashboard" element={<AsyncPage><Dashboard /></AsyncPage>} />
      <Route path="/property-needs" element={<PropertyNeeds />} />
      <Route path="/favorites" element={<Favorites />} />
      <Route path="/compare" element={<PropertyComparison />} />
      <Route path="/admin/market-references" element={<AsyncPage><MarketReferences /></AsyncPage>} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}

function AppShell() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  useEffect(
    () =>
      onAuthenticationLost(() => {
        const returnTo = window.location.pathname
        if (['/dashboard', '/property-needs', '/favorites'].includes(returnTo))
          navigate('/login', { replace: true, state: { returnTo } })
      }),
    [navigate],
  )
  useEffect(() => {
    document.title = interfaceTitle(pathname)
    document.getElementById('main-content')?.focus({ preventScroll: true })
    window.scrollTo(0, 0)
  }, [pathname])
  useEffect(() => {
    const reveal = (event) => {
      if (event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
      const anchor = event.target instanceof Element ? event.target.closest('a[href^="#"]') : null
      if (!anchor) return
      try { if (revealAnchorTarget(decodeURIComponent(anchor.getAttribute('href').slice(1)))) event.preventDefault() } catch { /* Invalid fragment: let the browser handle it. */ }
    }
    document.addEventListener('click', reveal)
    return () => document.removeEventListener('click', reveal)
  }, [])

  return (
    <>
      <a href="#main-content" className="skip-link">
        انتقل إلى المحتوى
      </a>
      <Navbar />
      <ComparisonSelection />
      <main id="main-content" className="main-content" tabIndex={-1}>
        <AppRoutes />
      </main>
      <Footer />
    </>
  )
}

export default function App() { return <FavoritesProvider><ComparisonProvider><AppShell /></ComparisonProvider></FavoritesProvider> }
