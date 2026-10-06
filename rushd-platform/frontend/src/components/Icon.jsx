const paths = {
  arrow: <path d="M19 12H5m6-6-6 6 6 6" />,
  home: (
    <>
      <path d="m3 10 9-7 9 7v10H3z" />
      <path d="M9 20v-7h6v7" />
    </>
  ),
  chart: <path d="M4 4v16h16M8 15l4-5 4 2 4-6" />,
  compare: <path d="M4 6h16M4 18h16m-4-15 4 3-4 3M8 15l-4 3 4 3" />,
  pin: (
    <>
      <path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" />
      <circle cx="12" cy="10" r="2" />
    </>
  ),
  heart: (
    <path d="M20.8 5.5c-2.2-2.3-6-1.6-8.8 1.3C9.2 3.9 5.4 3.2 3.2 5.5c-2.8 3 0 7.1 8.8 13.5 8.8-6.4 11.6-10.5 8.8-13.5Z" />
  ),
  shield: (
    <>
      <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z" />
      <path d="m8 12 3 3 5-6" />
    </>
  ),
  info: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v6M12 7h.01" />
    </>
  ),
  lock: (
    <>
      <rect x="5" y="10" width="14" height="11" rx="2" />
      <path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" />
    </>
  ),
  check: <path d="m5 12 4 4L19 6" />,
  menu: <path d="M4 6h16M4 12h16M4 18h16" />,
  close: <path d="m6 6 12 12M6 18 18 6" />,
  area: (
    <>
      <rect x="4" y="4" width="16" height="16" rx="2" />
      <path d="M8 4v4M12 4v3M16 4v4M4 8h4M4 12h3M4 16h4" />
    </>
  ),
  document: <path d="M14 3H5v18h14V8zM14 3v5h5M8 12h8M8 16h6" />,
}

function Icon({ name, className = '' }) {
  return (
    <svg
      className={`icon ${className}`}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name] || paths.home}
    </svg>
  )
}

export default Icon
