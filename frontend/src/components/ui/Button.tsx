import type { ButtonHTMLAttributes, ReactNode } from 'react';
import './Button.css';

export type ButtonVariant = 'primary' | 'primary-on-dark' | 'ghost' | 'text-link';

interface ButtonProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onClick'> {
  variant?: ButtonVariant;
  loading?: boolean;
  children: ReactNode;
  onClick?: () => void;
}

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'bb-btn--primary',
  'primary-on-dark': 'bb-btn--primary-on-dark',
  ghost: 'bb-btn--ghost',
  'text-link': 'bb-btn--text-link',
};

/**
 * DESIGN.md button-primary / button-primary-on-dark / button-ghost / button-text-link.
 * 검정 알약(rounded.full), 높이 40px(모바일 48px), typography.button. 크기 변형 없음.
 */
export function Button({
  variant = 'primary',
  loading = false,
  disabled,
  children,
  onClick,
  className,
  type = 'button',
  ...rest
}: ButtonProps) {
  const isDisabled = disabled || loading;

  return (
    <button
      type={type}
      className={`bb-btn ${VARIANT_CLASS[variant]}${className ? ` ${className}` : ''}`}
      disabled={isDisabled}
      aria-busy={loading}
      onClick={loading ? undefined : onClick}
      {...rest}
    >
      {loading && <span className="bb-btn__spinner" aria-hidden="true" />}
      <span className="bb-btn__label">{children}</span>
    </button>
  );
}
