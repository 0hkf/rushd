import { useState } from 'react'

export default function PasswordInput(props) {
  const [visible, setVisible] = useState(false)
  return <div className="password-input">
    <input {...props} type={visible ? 'text' : 'password'} />
    <button type="button" aria-label={visible ? 'إخفاء كلمة المرور' : 'إظهار كلمة المرور'} aria-pressed={visible} aria-controls={props.id} disabled={props.disabled} onClick={() => setVisible((value) => !value)}>{visible ? 'إخفاء' : 'إظهار'}</button>
  </div>
}
