import { api } from './api';
import type { User, UserInput } from '../types/api';

export const usersService = {
  async list() { return (await api.get<User[]>('/users')).data; },
  async create(input: UserInput) { return (await api.post<User>('/users', input)).data; },
  async update(id: string, input: Omit<UserInput, 'password'>) { return (await api.put<User>(`/users/${id}`, input)).data; },
  async remove(id: string) { await api.delete(`/users/${id}`); },
};
