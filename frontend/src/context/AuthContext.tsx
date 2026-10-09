import React, { createContext, useContext, useState, useEffect } from 'react';
import { User } from '../types';
import { api } from '../services/api';
import { socket } from '../services/socket';

interface AuthContextType {
  user: User | null;
  token: string | null;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const savedToken = localStorage.getItem('minicord_token');
    const savedUser = localStorage.getItem('minicord_user');

    if (savedToken && savedUser) {
      try {
        setToken(savedToken);
        setUser(JSON.parse(savedUser));
        socket.connect(savedToken);
      } catch (e) {
        localStorage.removeItem('minicord_token');
        localStorage.removeItem('minicord_user');
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (email: string, password: string) => {
    const data = await api.auth.login(email, password);
    const u: User = { id: data.id, username: data.username, email };
    setUser(u);
    setToken(data.token);
    localStorage.setItem('minicord_token', data.token);
    localStorage.setItem('minicord_user', JSON.stringify(u));
    socket.connect(data.token);
  };

  const register = async (username: string, email: string, password: string) => {
    const data = await api.auth.register(username, email, password);
    const u: User = { id: data.id, username: data.username, email: data.email };
    setUser(u);
    setToken(data.token);
    localStorage.setItem('minicord_token', data.token);
    localStorage.setItem('minicord_user', JSON.stringify(u));
    socket.connect(data.token);
  };

  const logout = () => {
    api.auth.logout().catch(() => {});
    setUser(null);
    setToken(null);
    localStorage.removeItem('minicord_token');
    localStorage.removeItem('minicord_user');
    socket.disconnect();
  };

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout, isLoading }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
};
