import type { InputHTMLAttributes } from 'react';
import './Field.css';

interface FieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'id' | 'onChange'> {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  hint?: string;
}

/**
 * DESIGN.md form-field / form-field-focused / form-field-error.
 * 테두리 박스가 아니라 1px 밑줄(hairline-soft), 포커스 시 ink로 진해짐. 라운딩 없음.
 */
export function Field({ id, label, value, onChange, error, hint, ...rest }: FieldProps) {
  const helperId = error ? `${id}-error` : hint ? `${id}-hint` : undefined;

  return (
    <div className="bb-field">
      <label className="bb-field__label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        className={`bb-field__input${error ? ' bb-field__input--error' : ''}`}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={helperId}
        {...rest}
      />
      {error && (
        <p id={helperId} className="bb-field__error">
          {error}
        </p>
      )}
      {!error && hint && (
        <p id={helperId} className="bb-field__hint">
          {hint}
        </p>
      )}
    </div>
  );
}
