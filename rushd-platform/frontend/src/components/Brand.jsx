export function BrandMark({ className = '' }) {
  return (
    <svg className={`brand-mark ${className}`} viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <path
        d="M6 39V22l12-9v26M18 39V9l12-5v35M30 39V18l12 7v14M4 43h40"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path
        d="M23 15v4m0 6v4M11 26v6M35 27v5"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
      />
    </svg>
  )
}

function Brand() {
  return (
    <span className="brand">
      <BrandMark />
      <span className="brand-name">
        <strong>روافد</strong>
        <span>العقارية</span>
      </span>
    </span>
  )
}

export default Brand
